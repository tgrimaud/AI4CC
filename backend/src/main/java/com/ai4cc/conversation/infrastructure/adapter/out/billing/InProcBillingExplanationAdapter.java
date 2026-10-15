package com.ai4cc.conversation.infrastructure.adapter.out.billing;

import com.ai4cc.billing.domain.model.BillingExplanation;
import com.ai4cc.billing.domain.model.valueobject.BillingExplanationQuery;
import com.ai4cc.billing.domain.port.in.ExplainBillingUseCase;
import com.ai4cc.conversation.domain.model.valueobject.BillingExplanationRequest;
import com.ai4cc.conversation.domain.model.valueobject.BillingGrounding;
import com.ai4cc.conversation.domain.model.valueobject.EscalationReason;
import com.ai4cc.conversation.domain.port.out.BillingExplanationPort;
import com.ai4cc.shared.observability.BackendTelemetry;
import com.ai4cc.shared.observability.CorrelationId;
import com.ai4cc.shared.observability.Slices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

// Outbound seam from the answer engine to the billing context (TASK-BE-045, ADR-0052), mirroring the
// knowledge retrieval seam (ADR-0027). It translates the conversation-owned request into a billing
// query, invokes the billing use case, and maps the deterministic BillingExplanation onto a
// conversation BillingGrounding (including the escalation-reason mapping) so the answer engine never
// references billing types. Records the BILLING latency slice tagged by outcome — technical
// dimensions only, never the transcript, customer reference or invoice content (privacy-safe).
public class InProcBillingExplanationAdapter implements BillingExplanationPort {

    private static final Logger log = LoggerFactory.getLogger(InProcBillingExplanationAdapter.class);
    private static final String PROVIDER = "billing";

    private final ExplainBillingUseCase explainBilling;
    private final BackendTelemetry telemetry;

    public InProcBillingExplanationAdapter(ExplainBillingUseCase explainBilling, BackendTelemetry telemetry) {
        this.explainBilling = explainBilling;
        this.telemetry = telemetry;
    }

    @Override
    public BillingGrounding explain(BillingExplanationRequest request) {
        long start = System.nanoTime();
        String outcome = "error";
        String reason = null;
        try {
            BillingExplanation explanation = explainBilling.explain(toQuery(request));
            outcome = explanation.outcome().name().toLowerCase(java.util.Locale.ROOT);
            reason = explanation.reason();
            logCauses(outcome, explanation.causeCodes());
            return toGrounding(explanation);
        } finally {
            telemetry.recordLatency(
                    Slices.BILLING, PROVIDER, outcome, reason, Duration.ofNanos(System.nanoTime() - start));
        }
    }

    private static BillingExplanationQuery toQuery(BillingExplanationRequest request) {
        return BillingExplanationQuery.of(request.channel(), request.transcript(), request.reference(),
                request.invoiceId(), request.languageCode());
    }

    private static BillingGrounding toGrounding(BillingExplanation explanation) {
        if (explanation.answerable()) {
            return BillingGrounding.answerable(explanation.text(), explanation.confidence());
        }
        if (explanation.escalate()) {
            return BillingGrounding.escalate(explanation.text(), reasonOf(explanation.escalationCode()));
        }
        return BillingGrounding.message(explanation.text());
    }

    // Maps the billing context's stable escalation code onto the backend-owned EscalationReason
    // (ADR-0019). Kept in the infrastructure seam so neither domain depends on the other's types.
    private static EscalationReason reasonOf(String escalationCode) {
        if (BillingExplanation.CODE_IDENTITY_UNVERIFIED.equals(escalationCode)) {
            return EscalationReason.IDENTITY_UNVERIFIED;
        }
        return EscalationReason.BILLING_UNEXPLAINED;
    }

    // Enum names only. Amounts and labels stay out of the log (ADR-0057).
    private static void logCauses(String outcome, List<String> causeCodes) {
        log.info("[BILLING] outcome={} causes={} correlation_id={}",
                outcome, joinCauses(causeCodes), CorrelationId.current());
    }

    private static String joinCauses(List<String> causeCodes) {
        if (causeCodes == null || causeCodes.isEmpty()) {
            return "n/a";
        }
        String joined = causeCodes.stream().filter(InProcBillingExplanationAdapter::causeCode)
                .collect(Collectors.joining(","));
        return joined.isBlank() ? "n/a" : joined;
    }

    private static boolean causeCode(String code) {
        return code != null && code.matches("[A-Z0-9_]+");
    }
}
