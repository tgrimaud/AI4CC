package com.ai4cc.billing.domain.service;

import com.ai4cc.billing.domain.model.BillingCause;
import com.ai4cc.billing.domain.model.BillingCauseType;
import com.ai4cc.billing.domain.model.ChangeKind;
import com.ai4cc.billing.domain.model.Invoice;
import com.ai4cc.billing.domain.model.InvoiceComparison;
import com.ai4cc.billing.domain.model.InvoiceItem;
import com.ai4cc.billing.domain.model.LineDelta;
import com.ai4cc.billing.domain.model.valueobject.LineCategory;
import com.ai4cc.billing.domain.model.valueobject.Money;
import com.ai4cc.billing.domain.port.in.CompareInvoicesUseCase;

import java.util.ArrayList;
import java.util.Currency;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// Deterministic invoice comparison (ADR-0003, ADR-0057, DEC-002): matches lines by code across
// the two invoices, computes each line's signed contribution on the tax-included (TTC) basis,
// attributes it to a catalogue cause, and exposes the residual no named cause accounts for
// (BR-003 — never hidden). No LLM, no guessing: the engine only does exact arithmetic.
public class InvoiceComparisonService implements CompareInvoicesUseCase {

    private final InvoiceCauseAttributor attributor;

    public InvoiceComparisonService() {
        this(List.of(), List.of());
    }

    public InvoiceComparisonService(List<String> lateFeeLabels, List<String> deviceInstallmentLabels) {
        this.attributor = new InvoiceCauseAttributor(lateFeeLabels, deviceInstallmentLabels);
    }

    @Override
    public InvoiceComparison compare(Invoice previous, Invoice current) {
        Objects.requireNonNull(previous, "previous invoice must not be null");
        Objects.requireNonNull(current, "current invoice must not be null");
        Money totalDelta = current.totals().taxIncluded().minus(previous.totals().taxIncluded());
        Currency currency = totalDelta.currency();
        List<PairedLine> pairs = lineDeltas(previous, current);
        List<AttributedLine> categorized = attributor.attribute(pairs, stayedSubscriptionTexts(previous, current));
        List<LineDelta> deltas = categorized.stream().map(AttributedLine::delta).toList();
        List<BillingCause> causes = causes(categorized, currency);
        // The residual is what no *identified* business cause accounts for: the arithmetic gap between
        // the header total and the line deltas PLUS any amount that fell into the UNEXPLAINED bucket
        // (a bare subscription move, a payment, an unmapped line). A line being present is not the same
        // as being explained, so UNEXPLAINED-category amounts stay in the residual and the confidence
        // gate can escalate them (fixes the "explained by an unexplained part" contradiction).
        Money unexplained = totalDelta.minus(explainedSum(categorized, currency));
        return new InvoiceComparison(previous, current, totalDelta, deltas, causes, unexplained);
    }

    private List<PairedLine> lineDeltas(Invoice previous, Invoice current) {
        Map<String, InvoiceItem> previousLines = index(previous.lines());
        Map<String, InvoiceItem> currentLines = index(current.lines());
        List<PairedLine> result = new ArrayList<>();
        for (String key : union(previousLines, currentLines)) {
            PairedLine delta = delta(previousLines.get(key), currentLines.get(key));
            if (delta != null) {
                result.add(delta);
            }
        }
        return result;
    }

    private PairedLine delta(InvoiceItem previous, InvoiceItem current) {
        InvoiceItem present = current != null ? current : previous;
        Currency currency = present.amounts().taxIncluded().currency();
        Money previousAmount = previous != null ? previous.amounts().taxIncluded() : Money.zero(currency);
        Money currentAmount = current != null ? current.amounts().taxIncluded() : Money.zero(currency);
        Money contribution = currentAmount.minus(previousAmount);
        if (contribution.isZero()) {
            return null;
        }
        ChangeKind changeKind = kind(previous, current);
        LineDelta lineDelta = new LineDelta(label(present), changeKind, previousAmount, currentAmount, contribution);
        return new PairedLine(previous, current, lineDelta);
    }

    private static List<String> stayedSubscriptionTexts(Invoice previous, Invoice current) {
        Set<String> before = subscriptionKeys(previous);
        List<String> texts = new ArrayList<>();
        for (InvoiceItem line : current.lines()) {
            if (line.category() == LineCategory.SUBSCRIPTION && before.contains(label(line))) {
                texts.add(InvoiceLineText.normalize(InvoiceLineText.describe(line)));
            }
        }
        return texts;
    }

    private static Set<String> subscriptionKeys(Invoice invoice) {
        Set<String> keys = new HashSet<>();
        for (InvoiceItem line : invoice.lines()) {
            if (line.category() == LineCategory.SUBSCRIPTION) {
                keys.add(label(line));
            }
        }
        return keys;
    }

    private List<BillingCause> causes(List<AttributedLine> categorized, Currency currency) {
        Map<BillingCauseType, List<LineDelta>> byCause = new EnumMap<>(BillingCauseType.class);
        for (AttributedLine item : categorized) {
            if (item.cause() != null) {
                byCause.computeIfAbsent(item.cause(), key -> new ArrayList<>()).add(item.delta());
            }
        }
        List<BillingCause> result = new ArrayList<>();
        for (BillingCauseType type : BillingCauseType.values()) {
            // UNEXPLAINED is not a business cause: its amount is surfaced as the residual, never voiced
            // as if it explained the change.
            if (type == BillingCauseType.UNEXPLAINED) {
                continue;
            }
            List<LineDelta> deltas = byCause.get(type);
            if (deltas != null) {
                result.add(new BillingCause(type, sum(deltas, currency), deltas));
            }
        }
        return result;
    }

    // Sum of the contributions attributed to an identified business cause (everything except the
    // UNEXPLAINED bucket) — the part the explanation can actually stand behind.
    private static Money explainedSum(List<AttributedLine> categorized, Currency currency) {
        return categorized.stream()
                .filter(AttributedLine::countsAsExplained)
                .map(item -> item.delta().contribution())
                .reduce(Money.zero(currency), Money::plus);
    }

    // Indexes lines by their matching key. A line is NEVER silently dropped (BUG-028): if two lines
    // share the same key within one invoice (e.g. a source that reuses a code), the repeats are
    // disambiguated so each still contributes to the comparison rather than vanishing from the diff.
    private static Map<String, InvoiceItem> index(List<InvoiceItem> lines) {
        Map<String, InvoiceItem> byKey = new LinkedHashMap<>();
        for (InvoiceItem line : lines) {
            byKey.put(disambiguate(byKey, label(line)), line);
        }
        return byKey;
    }

    private static String disambiguate(Map<String, InvoiceItem> byKey, String key) {
        if (!byKey.containsKey(key)) {
            return key;
        }
        int occurrence = 2;
        while (byKey.containsKey(key + "#" + occurrence)) {
            occurrence++;
        }
        return key + "#" + occurrence;
    }

    private static Set<String> union(Map<String, InvoiceItem> previous, Map<String, InvoiceItem> current) {
        Set<String> keys = new LinkedHashSet<>(previous.keySet());
        keys.addAll(current.keySet());
        return keys;
    }

    private static Money sum(List<LineDelta> deltas, Currency currency) {
        return deltas.stream().map(LineDelta::contribution).reduce(Money.zero(currency), Money::plus);
    }

    private static ChangeKind kind(InvoiceItem previous, InvoiceItem current) {
        if (previous == null) {
            return ChangeKind.APPEARED;
        }
        if (current == null) {
            return ChangeKind.DISAPPEARED;
        }
        return ChangeKind.CHANGED;
    }

    private static String label(InvoiceItem item) {
        if (item.code() != null && !item.code().isBlank()) {
            return item.code();
        }
        return item.type() != null ? item.type() : item.category().name();
    }

}
