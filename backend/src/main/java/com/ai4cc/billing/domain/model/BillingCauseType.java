package com.ai4cc.billing.domain.model;

// Business reason a set of line deltas is attributed to (ADR-0057). UNEXPLAINED is the fail-closed
// bucket: it is surfaced as residual and can gate the explanation (BR-003), and it is never voiced
// as a cause. CHANGE_CYCLE stays in the catalogue but is not attributed from invoice lines alone.
public enum BillingCauseType {
    PLAN_CHANGE,
    TARIFF_INCREASE,
    SPEED_UPGRADE,
    ADDON_ADDED,
    ADDON_REMOVED,
    DISCOUNT_ENDED,
    DISCOUNT_ADDED,
    DEVICE_INSTALLMENT_ENDED,
    LATE_PAYMENT_FEE,
    CHANGE_CYCLE,
    USAGE_OVERAGE,
    TAX_CHANGE,
    BILLING_ADJUSTMENT,
    ACTIVATION_FEE,
    ONE_OFF_BUNDLE,
    UNEXPLAINED
}
