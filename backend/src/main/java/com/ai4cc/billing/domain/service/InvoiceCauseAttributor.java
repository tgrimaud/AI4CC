package com.ai4cc.billing.domain.service;

import com.ai4cc.billing.domain.model.BillingCauseType;
import com.ai4cc.billing.domain.model.ChangeKind;
import com.ai4cc.billing.domain.model.InvoiceItem;
import com.ai4cc.billing.domain.model.LineDelta;
import com.ai4cc.billing.domain.model.valueobject.LineCategory;
import com.ai4cc.billing.domain.model.valueobject.Money;

import java.util.ArrayList;
import java.util.List;

// Attributes each line delta to the ADR-0057 catalogue. Deterministic: two invoices only, no LLM.
// Late-fee and device-installment labels are an explicit list; empty means those causes do not fire.
final class InvoiceCauseAttributor {

    private static final long TAX_TOLERANCE_CENTS = 1L;

    private final List<String> lateFeeLabels;
    private final List<String> installmentLabels;

    InvoiceCauseAttributor(List<String> lateFeeLabels, List<String> installmentLabels) {
        this.lateFeeLabels = InvoiceLineText.normalizeAll(lateFeeLabels);
        this.installmentLabels = InvoiceLineText.normalizeAll(installmentLabels);
    }

    List<AttributedLine> attribute(List<PairedLine> pairs, List<String> stayedSubscriptions) {
        boolean subscriptionStayed = !stayedSubscriptions.isEmpty();
        List<Row> rows = new ArrayList<>();
        for (PairedLine pair : pairs) {
            rows.add(new Row(pair, initial(pair, subscriptionStayed)));
        }
        reclassifyReplacement(rows, subscriptionStayed);
        linkProrata(rows);
        RolledOffAddOn.link(rows, stayedSubscriptions);
        absorbFollowingTax(rows);
        return rows.stream().map(Row::toLine).toList();
    }

    private BillingCauseType initial(PairedLine pair, boolean subscriptionStayed) {
        return switch (pair.category()) {
            case USAGE, OVERAGE -> BillingCauseType.USAGE_OVERAGE;
            case ADJUSTMENT -> BillingCauseType.BILLING_ADJUSTMENT;
            case DISCOUNT -> discount(pair);
            case OPTION -> option(pair);
            case SUBSCRIPTION -> subscription(pair, subscriptionStayed);
            case ONE_OFF -> oneOff(pair);
            case PRORATA, TAX -> null;
            default -> BillingCauseType.UNEXPLAINED;
        };
    }

    private static BillingCauseType discount(PairedLine pair) {
        return switch (pair.kind()) {
            case DISAPPEARED -> BillingCauseType.DISCOUNT_ENDED;
            case APPEARED -> BillingCauseType.DISCOUNT_ADDED;
            default -> BillingCauseType.UNEXPLAINED;
        };
    }

    private static BillingCauseType option(PairedLine pair) {
        return switch (pair.kind()) {
            case APPEARED -> BillingCauseType.ADDON_ADDED;
            case DISAPPEARED -> BillingCauseType.ADDON_REMOVED;
            default -> BillingCauseType.UNEXPLAINED;
        };
    }

    // A subscription beside one that stayed is an add-on. A lone replacement is decided later.
    private static BillingCauseType subscription(PairedLine pair, boolean subscriptionStayed) {
        if (pair.kind() == ChangeKind.CHANGED) {
            return changedSubscription(pair);
        }
        if (!subscriptionStayed) {
            return null;
        }
        return addonFor(pair.kind());
    }

    private static BillingCauseType changedSubscription(PairedLine pair) {
        String before = InvoiceLineText.describe(pair.previous());
        String after = InvoiceLineText.describe(pair.current());
        if (!InvoiceLineText.normalize(before).equals(InvoiceLineText.normalize(after))) {
            return InvoiceLineText.speedUpgrade(before, after, pair.delta().contribution())
                    ? BillingCauseType.SPEED_UPGRADE : BillingCauseType.PLAN_CHANGE;
        }
        if (pair.delta().contribution().isNegative() || pair.delta().contribution().isZero()) {
            return BillingCauseType.UNEXPLAINED;
        }
        return BillingCauseType.TARIFF_INCREASE;
    }

    private BillingCauseType oneOff(PairedLine pair) {
        String text = pair.text();
        if (InvoiceLineText.containsAny(lateFeeLabels, text)) {
            return BillingCauseType.LATE_PAYMENT_FEE;
        }
        if (pair.kind() == ChangeKind.DISAPPEARED && InvoiceLineText.containsAny(installmentLabels, text)) {
            return BillingCauseType.DEVICE_INSTALLMENT_ENDED;
        }
        if (!appearedOrGone(pair.kind())) {
            return BillingCauseType.UNEXPLAINED;
        }
        if (InvoiceLineText.activation(text)) {
            return BillingCauseType.ACTIVATION_FEE;
        }
        return InvoiceLineText.oneOffBundle(text)
                ? BillingCauseType.ONE_OFF_BUNDLE : BillingCauseType.UNEXPLAINED;
    }

    private static void reclassifyReplacement(List<Row> rows, boolean subscriptionStayed) {
        if (subscriptionStayed) {
            return;
        }
        List<Row> pending = pendingSubscriptions(rows);
        if (count(pending, ChangeKind.APPEARED) == 1 && count(pending, ChangeKind.DISAPPEARED) == 1) {
            BillingCauseType cause = replacementCause(pending);
            pending.forEach(row -> row.cause = cause);
            return;
        }
        pending.forEach(row -> row.cause = addonFor(row.pair.kind()));
    }

    private static BillingCauseType replacementCause(List<Row> pending) {
        String before = textOf(pending, ChangeKind.DISAPPEARED);
        String after = textOf(pending, ChangeKind.APPEARED);
        Money net = pending.stream().map(row -> row.pair.delta().contribution())
                .reduce(Money.zero(pending.get(0).pair.delta().contribution().currency()), Money::plus);
        if (InvoiceLineText.speedUpgrade(before, after, net)) {
            return BillingCauseType.SPEED_UPGRADE;
        }
        return BillingCauseType.PLAN_CHANGE;
    }

    // An appearing prorata joins a new service. A prorata that drops off is handled by RolledOffAddOn.
    private static void linkProrata(List<Row> rows) {
        List<String> added = addedServiceLabels(rows);
        for (Row row : rows) {
            if (row.pair.category() != LineCategory.PRORATA) {
                continue;
            }
            row.cause = row.pair.kind() == ChangeKind.APPEARED && InvoiceLineText.belongsTo(row.pair.text(), added)
                    ? BillingCauseType.ADDON_ADDED : BillingCauseType.UNEXPLAINED;
            row.explained = named(row.cause);
        }
    }

    private static void absorbFollowingTax(List<Row> rows) {
        List<Row> taxes = rows.stream().filter(row -> row.pair.category() == LineCategory.TAX).toList();
        if (taxes.isEmpty() || !namedCauseExists(rows)) {
            taxes.forEach(tax -> tax.cause = BillingCauseType.TAX_CHANGE);
            return;
        }
        long otherTax = rows.stream()
                .filter(row -> row.pair.category() != LineCategory.TAX && named(row.cause))
                .mapToLong(row -> taxDelta(row.pair)).sum();
        long taxSum = taxes.stream().mapToLong(row -> row.pair.delta().contribution().minorUnits()).sum();
        boolean follows = Math.abs(taxSum - otherTax) <= TAX_TOLERANCE_CENTS;
        for (Row tax : taxes) {
            tax.cause = follows ? null : BillingCauseType.TAX_CHANGE;
            tax.explained = true;
        }
    }

    private static List<String> addedServiceLabels(List<Row> rows) {
        List<String> labels = new ArrayList<>();
        for (Row row : rows) {
            if (row.cause != BillingCauseType.ADDON_ADDED || !serviceOrOption(row.pair.category())) {
                continue;
            }
            String label = InvoiceLineText.normalize(row.pair.text());
            if (label.length() >= 4) {
                labels.add(label);
            }
        }
        return labels;
    }

    private static boolean serviceOrOption(LineCategory category) {
        return category == LineCategory.SUBSCRIPTION || category == LineCategory.OPTION;
    }

    private static long taxDelta(PairedLine pair) {
        long current = pair.current() == null ? 0L : pair.current().amounts().tax().minorUnits();
        long previous = pair.previous() == null ? 0L : pair.previous().amounts().tax().minorUnits();
        return current - previous;
    }

    private static boolean namedCauseExists(List<Row> rows) {
        return rows.stream().anyMatch(row -> named(row.cause));
    }

    private static boolean named(BillingCauseType cause) {
        return cause != null && cause != BillingCauseType.UNEXPLAINED;
    }

    private static List<Row> pendingSubscriptions(List<Row> rows) {
        return rows.stream()
                .filter(row -> row.cause == null && row.pair.category() == LineCategory.SUBSCRIPTION)
                .toList();
    }

    private static long count(List<Row> rows, ChangeKind kind) {
        return rows.stream().filter(row -> row.pair.kind() == kind).count();
    }

    private static String textOf(List<Row> rows, ChangeKind kind) {
        return rows.stream().filter(row -> row.pair.kind() == kind).map(row -> row.pair.text())
                .findFirst().orElse("");
    }

    private static BillingCauseType addonFor(ChangeKind kind) {
        return kind == ChangeKind.DISAPPEARED ? BillingCauseType.ADDON_REMOVED : BillingCauseType.ADDON_ADDED;
    }

    private static boolean appearedOrGone(ChangeKind kind) {
        return kind == ChangeKind.APPEARED || kind == ChangeKind.DISAPPEARED;
    }

    static final class Row {
        final PairedLine pair;
        BillingCauseType cause;
        boolean explained;

        private Row(PairedLine pair, BillingCauseType cause) {
            this.pair = pair;
            this.cause = cause;
            this.explained = named(cause);
        }

        private AttributedLine toLine() {
            if (explained && cause == null) {
                return new AttributedLine(pair.delta(), null, true);
            }
            if (!named(cause)) {
                return new AttributedLine(pair.delta(), null, false);
            }
            return new AttributedLine(pair.delta(), cause, true);
        }
    }
}

record PairedLine(InvoiceItem previous, InvoiceItem current, LineDelta delta) {

    LineCategory category() {
        return (current != null ? current : previous).category();
    }

    ChangeKind kind() {
        return delta.kind();
    }

    String text() {
        return InvoiceLineText.describe(current != null ? current : previous);
    }
}

record AttributedLine(LineDelta delta, BillingCauseType cause, boolean countsAsExplained) {
}
