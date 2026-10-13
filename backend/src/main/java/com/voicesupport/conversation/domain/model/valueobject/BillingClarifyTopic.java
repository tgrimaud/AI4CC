package com.voicesupport.conversation.domain.model.valueobject;

// US-043 / TASK-BE-072 (OQ-043-b): a configurable billing problem SUB-TYPE used to STEER the single
// bounded clarify question (TASK-BE-071) toward the common billing issues this assistant can actually
// explain from the knowledge base and the comparison engine. The list is per-deployment and enrichable
// — adding, removing or relabelling a sub-type is config only. id = stable key (telemetry + a future
// escalation tag); fr/en = the short, voice-friendly hint phrasing woven into the clarify question in
// the answer language. A hint states no amount, so DEC-002 stays enforced by the OutputGuardrail.
public record BillingClarifyTopic(String id, String fr, String en) {

    public String hint(AnswerLanguage language) {
        return language == AnswerLanguage.FRENCH ? fr : en;
    }
}
