package com.ai4cc.billing.domain.service;

import com.ai4cc.billing.domain.model.BillingCause;
import com.ai4cc.billing.domain.model.BillingCauseType;
import com.ai4cc.billing.domain.model.ChangeKind;
import com.ai4cc.billing.domain.model.LineDelta;
import com.ai4cc.billing.domain.model.valueobject.Money;

import java.util.List;

// Keeps a partial period that has dropped off separate from a service that appeared on the same bill.
final class AddonFamilies {

    private AddonFamilies() {
    }

    static List<Money> impacts(BillingCause cause) {
        if (cause.type() != BillingCauseType.ADDON_ADDED) {
            return List.of(cause.impact());
        }
        Money dropped = null;
        Money added = null;
        for (LineDelta delta : cause.deltas()) {
            if (delta.kind() == ChangeKind.DISAPPEARED) {
                dropped = plus(dropped, delta);
            } else {
                added = plus(added, delta);
            }
        }
        if (dropped == null || added == null) {
            return List.of(cause.impact());
        }
        return List.of(dropped, added);
    }

    private static Money plus(Money total, LineDelta delta) {
        return total == null ? delta.contribution() : total.plus(delta.contribution());
    }
}
