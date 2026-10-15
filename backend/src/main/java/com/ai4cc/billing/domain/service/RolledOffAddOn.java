package com.ai4cc.billing.domain.service;

import com.ai4cc.billing.domain.model.BillingCauseType;
import com.ai4cc.billing.domain.model.ChangeKind;
import com.ai4cc.billing.domain.model.valueobject.LineCategory;

import java.util.List;

// The partial first days of an add-on are billed once, then absent. They stay that add-on.
// Only the discount prorata of that same short window, on that same service, stays with them.
final class RolledOffAddOn {

    private RolledOffAddOn() {
    }

    static void link(List<InvoiceCauseAttributor.Row> rows, List<String> stayed) {
        List<InvoiceCauseAttributor.Row> proratas = rolledOff(rows, stayed);
        if (proratas.isEmpty()) {
            return;
        }
        proratas.forEach(RolledOffAddOn::markAdded);
        for (InvoiceCauseAttributor.Row row : rows) {
            if (matchesWindow(row, proratas, stayed)) {
                markAdded(row);
            }
        }
    }

    private static List<InvoiceCauseAttributor.Row> rolledOff(
            List<InvoiceCauseAttributor.Row> rows, List<String> stayed) {
        return rows.stream().filter(row -> isProrata(row, stayed)).toList();
    }

    private static boolean isProrata(InvoiceCauseAttributor.Row row, List<String> stayed) {
        return row.pair.category() == LineCategory.PRORATA
                && row.pair.kind() == ChangeKind.DISAPPEARED
                && InvoiceLineText.belongsTo(row.pair.text(), stayed);
    }

    private static boolean matchesWindow(InvoiceCauseAttributor.Row row,
            List<InvoiceCauseAttributor.Row> proratas, List<String> stayed) {
        if (row.cause != BillingCauseType.DISCOUNT_ENDED) {
            return false;
        }
        return proratas.stream().anyMatch(prorata -> sameOffer(row, prorata, stayed));
    }

    private static boolean sameOffer(InvoiceCauseAttributor.Row discount,
            InvoiceCauseAttributor.Row prorata, List<String> stayed) {
        return InvoiceLineText.sameShortWindow(discount.pair.text(), prorata.pair.text())
                && sharedService(discount.pair.text(), prorata.pair.text(), stayed);
    }

    private static boolean sharedService(String discount, String prorata, List<String> stayed) {
        return stayed.stream().anyMatch(label -> onService(discount, label) && onService(prorata, label));
    }

    private static boolean onService(String text, String label) {
        return InvoiceLineText.belongsTo(text, List.of(label));
    }

    private static void markAdded(InvoiceCauseAttributor.Row row) {
        row.cause = BillingCauseType.ADDON_ADDED;
        row.explained = true;
    }
}
