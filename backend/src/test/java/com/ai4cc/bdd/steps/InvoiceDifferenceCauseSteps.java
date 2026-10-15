package com.ai4cc.bdd.steps;

import com.ai4cc.billing.domain.port.in.ExplainBillingUseCase;
import com.ai4cc.billing.domain.port.out.BssBillingPort;
import com.ai4cc.billing.domain.service.BillingExplanationComposer;
import com.ai4cc.billing.domain.service.BillingExplanationService;
import com.ai4cc.billing.domain.service.BillingIntentDetector;
import com.ai4cc.billing.domain.service.ComparableInvoiceService;
import com.ai4cc.billing.domain.service.ComparisonConfidenceService;
import com.ai4cc.billing.domain.service.CustomerIdentityService;
import com.ai4cc.billing.domain.service.InvoiceComparisonService;
import com.ai4cc.billing.infrastructure.adapter.out.bss.InMemoryBssBillingAdapter;
import com.ai4cc.billing.infrastructure.adapter.out.identity.InMemoryCustomerDirectoryAdapter;
import com.ai4cc.billing.infrastructure.fixtures.EirB2cSampleFixtures;
import com.ai4cc.conversation.application.service.BillingAnswerService;
import com.ai4cc.conversation.domain.model.valueobject.AnswerLanguage;
import com.ai4cc.conversation.domain.model.valueobject.BillingExplanationRequest;
import com.ai4cc.conversation.domain.model.valueobject.GeneratedAnswer;
import com.ai4cc.conversation.domain.model.valueobject.RetrievedEvidence;
import com.ai4cc.conversation.domain.port.out.AnswerGeneratorPort;
import com.ai4cc.conversation.domain.port.out.BillingExplanationPort;
import com.ai4cc.conversation.domain.service.LanguageDetector;
import com.ai4cc.conversation.domain.service.OutputGuardrail;
import com.ai4cc.conversation.infrastructure.adapter.out.billing.InProcBillingExplanationAdapter;
import com.ai4cc.shared.observability.BackendTelemetry;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InvoiceDifferenceCauseSteps {

    private EchoGenerator generator;
    private BillingAnswerService service;
    private String reference;
    private GeneratedAnswer answer;

    @Before
    public void setUp() {
        BssBillingPort bss = new InMemoryBssBillingAdapter(EirB2cSampleFixtures.all());
        ExplainBillingUseCase explainBilling = new BillingExplanationService(
                new BillingIntentDetector(List.of("facture", "augmente", "invoice", "bill")),
                new CustomerIdentityService(new InMemoryCustomerDirectoryAdapter()),
                new ComparableInvoiceService(bss), bss,
                new InvoiceComparisonService(), new ComparisonConfidenceService(0.05),
                new BillingExplanationComposer());
        BillingExplanationPort port = new InProcBillingExplanationAdapter(
                explainBilling, new BackendTelemetry(new SimpleMeterRegistry()));
        generator = new EchoGenerator();
        service = new BillingAnswerService(
                port, generator, new OutputGuardrail(), new LanguageDetector(AnswerLanguage.FRENCH));
        reference = null;
        answer = null;
    }

    @Given("the eir customer {string}")
    public void theEirCustomer(String account) {
        reference = account;
    }

    @Given("the assistant repeats the computed explanation")
    public void theAssistantRepeatsTheComputedExplanation() {
        generator.echo = true;
    }

    @When("the customer asks in French why their bill changed")
    public void asksInFrench() {
        answer = service.answer(request("Pourquoi ma facture a augmenté ce mois-ci ?", "fr"));
    }

    @When("the customer asks in English why their bill changed")
    public void asksInEnglish() {
        answer = service.answer(request("Why did my bill change this month?", "en"));
    }

    @When("the customer asks how to set up their router")
    public void asksAboutTheRouter() {
        answer = service.answer(request("Comment configurer mon routeur ?", "fr"));
    }

    @Then("the customer hears {string}")
    public void theCustomerHears(String fragment) {
        assertTrue(answer.text().contains(fragment), answer.text());
    }

    @Then("the customer does not hear {string}")
    public void theCustomerDoesNotHear(String fragment) {
        assertFalse(answer.text().contains(fragment), answer.text());
    }

    @Then("the customer is not transferred to an advisor")
    public void theCustomerIsNotTransferred() {
        assertFalse(answer.requiresEscalation(), answer.text());
        assertFalse(answer.text().toLowerCase().contains("conseiller"), answer.text());
    }

    @Then("the customer is transferred to an advisor")
    public void theCustomerIsTransferred() {
        assertTrue(answer.requiresEscalation(), answer.text());
        assertTrue(answer.text().toLowerCase().contains("conseiller"), answer.text());
    }

    @Then("the answer contains no amount")
    public void theAnswerContainsNoAmount() {
        assertFalse(answer.text().contains("€"), answer.text());
    }

    @Then("the language model is not called")
    public void theLanguageModelIsNotCalled() {
        assertEquals(0, generator.callCount);
    }

    private BillingExplanationRequest request(String transcript, String language) {
        return new BillingExplanationRequest(transcript, reference, null, language, "web", "conv-qa", "corr-qa");
    }

    private static final class EchoGenerator implements AnswerGeneratorPort {
        private boolean echo;
        private int callCount;

        @Override
        public String generate(
                String question, List<RetrievedEvidence> evidence, List<String> history, AnswerLanguage language) {
            callCount++;
            return echo && !evidence.isEmpty() ? evidence.get(0).text() : "";
        }
    }
}
