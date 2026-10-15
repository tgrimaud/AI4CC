package com.ai4cc.billing.domain.service;

import com.ai4cc.billing.domain.model.BillingExplanation;
import com.ai4cc.billing.domain.model.BillingExplanationOutcome;
import com.ai4cc.billing.domain.model.valueobject.BillingExplanationQuery;
import com.ai4cc.billing.domain.port.out.BssBillingPort;
import com.ai4cc.billing.infrastructure.adapter.out.bss.InMemoryBssBillingAdapter;
import com.ai4cc.billing.infrastructure.adapter.out.identity.InMemoryCustomerDirectoryAdapter;
import com.ai4cc.billing.infrastructure.fixtures.EirB2cSampleFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Billing cause catalogue smoke (three eir B2C accounts)")
class BillingCauseCatalogueSmokeTest {

    private static final String QUESTION = "Pourquoi ma facture a augmenté ce mois-ci ?";

    private final BillingExplanationService service = serviceOverEirSamples();

    @Test
    void account_99226126_is_spoken_in_french() {
        // GIVEN the fibre-plus-new-TV account, whose new discounts are named

        // WHEN the customer asks why the bill changed
        BillingExplanation explanation = explain("99226126", "fr");

        // THEN the customer hears the drop, the add-on, the new discount and the fee that ended
        assertThat(explanation.outcome()).isEqualTo(BillingExplanationOutcome.EXPLAINED);
        assertThat(explanation.escalate()).isFalse();
        assertThat(explanation.text()).contains("diminué de 66.51 €");
        assertThat(explanation.text()).contains("nouveau service", "+41.31 €");
        assertThat(explanation.text()).contains("début d'une remise", "-7.83 €");
        assertThat(explanation.text()).contains("frais d'activation", "-99.99 €");
        assertThat(explanation.text()).doesNotContain("conseiller");
        assertThat(explanation.causeCodes())
                .containsExactly("ADDON_ADDED", "DISCOUNT_ADDED", "ACTIVATION_FEE");
    }

    @Test
    void account_99226126_is_spoken_in_english() {
        // GIVEN the same account

        // WHEN the customer asks in English
        BillingExplanation explanation = explain("99226126", "en");

        // THEN the same three causes are spoken in English
        assertThat(explanation.outcome()).isEqualTo(BillingExplanationOutcome.EXPLAINED);
        assertThat(explanation.text()).contains("decreased by 66.51 €");
        assertThat(explanation.text()).contains("new service", "start of a discount", "activation fee");
    }

    @Test
    void account_99224964_explains_the_new_options_and_the_one_off_bundle() {
        // GIVEN the mobile account with a new 15GB option and a one-off roaming bundle

        // WHEN the customer asks why the bill changed
        BillingExplanation explanation = explain("99224964", "fr");

        // THEN both differences are spoken and the turn does not go to an advisor
        assertThat(explanation.outcome()).isEqualTo(BillingExplanationOutcome.EXPLAINED);
        assertThat(explanation.escalate()).isFalse();
        assertThat(explanation.text()).contains("augmenté de 55.47");
        assertThat(explanation.text()).contains("nouveau service", "25.48");
        assertThat(explanation.text()).contains("bundle ponctuel", "29.99");
        assertThat(explanation.text()).doesNotContain("conseiller");
        assertThat(explanation.causeCodes()).containsExactly("ADDON_ADDED", "ONE_OFF_BUNDLE");
    }

    @Test
    void account_99226337_explains_the_tv_partial_period_and_the_new_mobile() {
        // GIVEN fibre plus TV, with a new mobile on the later bill

        // WHEN the customer asks why the bill changed
        BillingExplanation explanation = explain("99226337", "fr");

        // THEN both add-ons are spoken and the turn does not go to an advisor
        assertThat(explanation.outcome()).isEqualTo(BillingExplanationOutcome.EXPLAINED);
        assertThat(explanation.escalate()).isFalse();
        assertThat(explanation.text()).contains("augmenté de 34.23");
        assertThat(explanation.text()).contains("101.82", "1.94");
        assertThat(explanation.text()).doesNotContain("fin d'une remise", "conseiller");
        assertThat(explanation.causeCodes())
                .containsExactly("ADDON_ADDED", "DISCOUNT_ADDED", "ACTIVATION_FEE");
    }

    @Test
    void an_unknown_reference_is_handed_to_an_advisor() {
        // GIVEN a billing question whose reference is not in the directory

        // WHEN the customer asks why the bill changed
        BillingExplanation explanation = explain("00000000", "fr");

        // THEN identity fails closed
        assertThat(explanation.outcome()).isEqualTo(BillingExplanationOutcome.IDENTITY_UNRESOLVED);
        assertThat(explanation.escalate()).isTrue();
        assertThat(explanation.text()).contains("conseiller");
        assertThat(explanation.causeCodes()).isEmpty();
    }

    @Test
    void a_non_billing_question_is_redirected() {
        // GIVEN a question that is not about the bill

        // WHEN it is submitted for the fibre-plus-TV account
        BillingExplanation explanation = service.explain(
                BillingExplanationQuery.of("web", "Comment configurer mon routeur ?", "99226126", null, "fr"));

        // THEN the catalogue is not applied
        assertThat(explanation.outcome()).isEqualTo(BillingExplanationOutcome.NOT_A_BILLING_REQUEST);
        assertThat(explanation.escalate()).isFalse();
        assertThat(explanation.text()).contains("facture");
        assertThat(explanation.causeCodes()).isEmpty();
    }

    private BillingExplanation explain(String reference, String language) {
        return service.explain(BillingExplanationQuery.of("web", QUESTION, reference, null, language));
    }

    private static BillingExplanationService serviceOverEirSamples() {
        BssBillingPort samples = new InMemoryBssBillingAdapter(EirB2cSampleFixtures.all());
        return new BillingExplanationService(
                new BillingIntentDetector(List.of("facture", "augmente", "invoice", "bill")),
                new CustomerIdentityService(new InMemoryCustomerDirectoryAdapter()),
                new ComparableInvoiceService(samples), samples,
                new InvoiceComparisonService(), new ComparisonConfidenceService(0.05),
                new BillingExplanationComposer());
    }
}
