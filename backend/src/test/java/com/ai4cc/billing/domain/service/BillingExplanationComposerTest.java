package com.ai4cc.billing.domain.service;

import com.ai4cc.billing.domain.model.BillingCause;
import com.ai4cc.billing.domain.model.BillingCauseType;
import com.ai4cc.billing.domain.model.ExplanationConfidence;
import com.ai4cc.billing.domain.model.ExplanationReadiness;
import com.ai4cc.billing.domain.model.Invoice;
import com.ai4cc.billing.domain.model.InvoiceComparison;
import com.ai4cc.billing.domain.model.ReadinessReason;
import com.ai4cc.billing.domain.model.valueobject.AccountId;
import com.ai4cc.billing.domain.model.valueobject.Money;
import com.ai4cc.billing.infrastructure.fixtures.BssBillingFixtures;
import com.ai4cc.billing.infrastructure.fixtures.EirB2cSampleFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Currency;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BillingExplanationComposer (grounded, language-aware text)")
class BillingExplanationComposerTest {

    private final InvoiceComparisonService comparisonService = new InvoiceComparisonService();
    private final ComparisonConfidenceService gate = new ComparisonConfidenceService(0.05);
    private final BillingExplanationComposer composer = new BillingExplanationComposer();

    @Test
    void composes_a_french_explanation_carrying_every_grounded_amount() {
        // GIVEN the discount-expiry journey (+500 cents = 5.00 €), fully reconciled
        InvoiceComparison comparison =
                comparisonService.compare(fixture("eir-002", "2026-01"), fixture("eir-002", "2026-02"));
        ExplanationReadiness readiness = gate.assess(comparison);

        // WHEN it is composed in French
        String text = composer.compose(comparison, readiness, "fr");

        // THEN the amount is present (so the OutputGuardrail passes) and the cause is named in French
        assertThat(text).contains("5.00 €");
        assertThat(text).contains("augmenté");
        assertThat(text).contains("remise");
    }

    @Test
    void composes_an_english_explanation_for_the_same_comparison() {
        // GIVEN the same discount-expiry comparison
        InvoiceComparison comparison =
                comparisonService.compare(fixture("eir-002", "2026-01"), fixture("eir-002", "2026-02"));
        ExplanationReadiness readiness = gate.assess(comparison);

        // WHEN composed in English
        String text = composer.compose(comparison, readiness, "en");

        // THEN it carries the amount and English wording
        assertThat(text).contains("5.00 €");
        assertThat(text).contains("increased");
        assertThat(text).contains("discount");
    }

    @Test
    void describes_an_identical_invoice_as_unchanged() {
        // GIVEN the nominal journey (identical invoices, zero delta)
        InvoiceComparison comparison =
                comparisonService.compare(fixture("eir-001", "2026-01"), fixture("eir-001", "2026-02"));
        ExplanationReadiness readiness = gate.assess(comparison);

        // WHEN composed
        String textFr = composer.compose(comparison, readiness, "fr");
        String textEn = composer.compose(comparison, readiness, "en");

        // THEN it states the bill is unchanged, in each language
        assertThat(textFr).contains("identique");
        assertThat(textEn).contains("unchanged");
    }

    @Test
    void voices_a_new_service_when_a_subscription_appears() {
        // GIVEN account 99226126 adding eir TV: a new service subscription appears (TASK-BE-067)
        InvoiceComparison comparison = comparisonService.compare(
                eirSample("99226126", "2608129000000040"), eirSample("99226126", "2609129000000009"));
        ExplanationReadiness readiness = gate.assess(comparison);

        // WHEN composed in each language
        String fr = composer.compose(comparison, readiness, "fr");
        String en = composer.compose(comparison, readiness, "en");

        // THEN the new service is named as a cause (not surfaced as an unexplained residual)
        assertThat(fr).contains("nouveau service");
        assertThat(en).contains("new service");
    }

    @Test
    void operational_messages_are_language_aware() {
        // GIVEN / WHEN / THEN the safe operational/hand-off messages are localized
        assertThat(composer.askReference("fr")).contains("référence");
        assertThat(composer.askReference("en")).contains("reference");
        assertThat(composer.cannotVerify("fr")).contains("conseiller");
        assertThat(composer.cannotVerify("en")).contains("advisor");
        assertThat(composer.notEnoughData("fr")).contains("conseiller");
        assertThat(composer.notABillingRequest("en")).contains("bill");
    }

    @Test
    void every_voiced_cause_has_a_french_and_an_english_phrase() {
        // GIVEN one comparison per catalogue cause, with a zero residual
        Invoice previous = fixture("eir-001", "2026-01");
        Invoice current = fixture("eir-001", "2026-02");
        Money impact = Money.ofMinorUnits(100L, Currency.getInstance("EUR"));
        Money none = Money.ofMinorUnits(0L, impact.currency());
        ExplanationReadiness readiness = new ExplanationReadiness(
                ExplanationConfidence.EXPLAINABLE, ReadinessReason.FULLY_EXPLAINED, false, none);
        Map<BillingCauseType, String> french = Map.ofEntries(
                Map.entry(BillingCauseType.PLAN_CHANGE, "changement d'offre"),
                Map.entry(BillingCauseType.TARIFF_INCREASE, "hausse de tarif"),
                Map.entry(BillingCauseType.SPEED_UPGRADE, "débit"),
                Map.entry(BillingCauseType.ADDON_ADDED, "nouveau service"),
                Map.entry(BillingCauseType.ADDON_REMOVED, "retrait d'un service"),
                Map.entry(BillingCauseType.DISCOUNT_ENDED, "fin d'une remise"),
                Map.entry(BillingCauseType.DISCOUNT_ADDED, "début d'une remise"),
                Map.entry(BillingCauseType.DEVICE_INSTALLMENT_ENDED, "mensualités"),
                Map.entry(BillingCauseType.LATE_PAYMENT_FEE, "frais de retard"),
                Map.entry(BillingCauseType.CHANGE_CYCLE, "cycle de facturation"),
                Map.entry(BillingCauseType.USAGE_OVERAGE, "dépassement"),
                Map.entry(BillingCauseType.TAX_CHANGE, "variation de taxe"),
                Map.entry(BillingCauseType.BILLING_ADJUSTMENT, "ajustement"),
                Map.entry(BillingCauseType.ACTIVATION_FEE, "frais d'activation"),
                Map.entry(BillingCauseType.ONE_OFF_BUNDLE, "bundle ponctuel"));
        Map<BillingCauseType, String> english = Map.ofEntries(
                Map.entry(BillingCauseType.PLAN_CHANGE, "plan change"),
                Map.entry(BillingCauseType.TARIFF_INCREASE, "tariff increase"),
                Map.entry(BillingCauseType.SPEED_UPGRADE, "speed change"),
                Map.entry(BillingCauseType.ADDON_ADDED, "new service"),
                Map.entry(BillingCauseType.ADDON_REMOVED, "service removed"),
                Map.entry(BillingCauseType.DISCOUNT_ENDED, "end of a discount"),
                Map.entry(BillingCauseType.DISCOUNT_ADDED, "start of a discount"),
                Map.entry(BillingCauseType.DEVICE_INSTALLMENT_ENDED, "device installment"),
                Map.entry(BillingCauseType.LATE_PAYMENT_FEE, "late-payment"),
                Map.entry(BillingCauseType.CHANGE_CYCLE, "billing-cycle"),
                Map.entry(BillingCauseType.USAGE_OVERAGE, "usage over"),
                Map.entry(BillingCauseType.TAX_CHANGE, "tax change"),
                Map.entry(BillingCauseType.BILLING_ADJUSTMENT, "adjustment"),
                Map.entry(BillingCauseType.ACTIVATION_FEE, "activation fee"),
                Map.entry(BillingCauseType.ONE_OFF_BUNDLE, "one-off bundle"));
        assertThat(french.keySet()).containsExactlyInAnyOrderElementsOf(
                java.util.Arrays.stream(BillingCauseType.values())
                        .filter(type -> type != BillingCauseType.UNEXPLAINED)
                        .toList());

        // WHEN each cause is composed
        for (BillingCauseType type : french.keySet()) {
            InvoiceComparison comparison = new InvoiceComparison(previous, current, impact, List.of(),
                    List.of(new BillingCause(type, impact, List.of())), none);
            String fr = composer.compose(comparison, readiness, "fr");
            String en = composer.compose(comparison, readiness, "en");

            // THEN the phrase is present and the unexplained bucket is not spoken as a cause
            assertThat(fr).contains(french.get(type));
            assertThat(en).contains(english.get(type));
            assertThat(fr).doesNotContain("non expliquée");
            assertThat(en).doesNotContain("unexplained part");
        }
    }

    private static Invoice fixture(String account, String periodSuffix) {
        return BssBillingFixtures.all().get(AccountId.of(account)).stream()
                .filter(invoice -> invoice.period().id().endsWith(periodSuffix))
                .findFirst().orElseThrow();
    }

    private static Invoice eirSample(String account, String billId) {
        return EirB2cSampleFixtures.all().get(AccountId.of(account)).stream()
                .filter(invoice -> invoice.id().value().equals(billId))
                .findFirst().orElseThrow();
    }
}
