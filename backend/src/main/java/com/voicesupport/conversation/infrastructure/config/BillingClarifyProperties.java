package com.voicesupport.conversation.infrastructure.config;

import com.voicesupport.conversation.domain.model.valueobject.BillingClarifyTopic;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

// US-043 / TASK-BE-072 (OQ-043-b): binds the per-deployment, enrichable list of billing problem
// SUB-TYPES from voice-support.conversation.billing-clarify.sub-types. Each entry is a stable id plus
// a short FR/EN hint phrasing. An empty/absent list falls back to the TASK-BE-071 generic clarify
// prompt; adding/removing/relabelling a sub-type is config only. max-questions under the same prefix
// is read separately (@Value) and is intentionally ignored here.
@ConfigurationProperties(prefix = "voice-support.conversation.billing-clarify")
public record BillingClarifyProperties(List<SubType> subTypes) {

    public BillingClarifyProperties {
        subTypes = subTypes == null ? List.of() : List.copyOf(subTypes);
    }

    // Maps the bound entries to the domain value object, dropping entries without a usable id so a
    // malformed deployment config degrades gracefully instead of steering with a blank sub-type.
    public List<BillingClarifyTopic> toTopics() {
        return subTypes.stream()
                .filter(subType -> subType != null && subType.id() != null && !subType.id().isBlank())
                .map(subType -> new BillingClarifyTopic(subType.id(), subType.fr(), subType.en()))
                .toList();
    }

    public record SubType(String id, String fr, String en) {
    }
}
