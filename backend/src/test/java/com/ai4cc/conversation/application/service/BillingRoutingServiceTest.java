package com.ai4cc.conversation.application.service;

import com.ai4cc.conversation.domain.model.valueobject.BillingExplanationRequest;
import com.ai4cc.conversation.domain.model.valueobject.ConversationTurn;
import com.ai4cc.conversation.domain.model.valueobject.GeneratedAnswer;
import com.ai4cc.conversation.domain.model.valueobject.RoutableTurn;
import com.ai4cc.conversation.domain.port.in.AnswerBillingQuestionUseCase;
import com.ai4cc.conversation.domain.port.out.ConversationMemoryPort;
import com.ai4cc.conversation.domain.service.AmountClarify;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// Single source of truth for the RAG-vs-billing decision (TASK-BE-061 / BUG-027): billing ONLY when
// the channel supplied an account reference AND the turn is a billing question; otherwise empty.
@DisplayName("BillingRoutingService (shared routing decision)")
class BillingRoutingServiceTest {

    private final RecordingBilling billing = new RecordingBilling();

    @Test
    void asks_before_explaining_any_invoice_request() {
        // GIVEN an identified customer who asks about the bill without naming an amount
        var memory = new Memory();
        var router = new BillingRoutingService(billing, transcript -> true, memory);
        var turn = new RoutableTurn("ma facture a augmenté", "conv-1", "fr", "99224964", "web_voice", "corr-1");

        // WHEN the turn is routed
        Optional<GeneratedAnswer> answer = router.billingAnswer(turn);

        // THEN the assistant asks which amount or service, and does not recite the bill
        assertThat(answer).isPresent();
        assertThat(answer.get().text()).isEqualTo(AmountClarify.QUESTION_FR);
        assertThat(answer.get().requiresEscalation()).isFalse();
        assertThat(billing.calls).isZero();
    }

    @Test
    void returns_empty_when_no_account_reference_even_if_billing_intent() {
        var router = new BillingRoutingService(billing, transcript -> true);
        var turn = new RoutableTurn("ma facture a augmenté", "conv-1", "fr", null, "web_voice", "corr-1");

        assertThat(router.billingAnswer(turn)).isEmpty();
        assertThat(billing.calls).isZero();
    }

    @Test
    void asks_which_amount_instead_of_reciting_the_bill() {
        // GIVEN an identified customer who does not understand an amount, without naming it
        var memory = new Memory();
        var router = new BillingRoutingService(billing, transcript -> true, memory);
        var turn = new RoutableTurn(
                "je ne comprends pas un montant sur ma facture", "conv-1", "fr", "99224964", "web_voice", "corr-1");

        // WHEN the turn is routed
        Optional<GeneratedAnswer> answer = router.billingAnswer(turn);

        // THEN the assistant asks which amount and does not explain the bill
        assertThat(answer).isPresent();
        assertThat(answer.get().text()).isEqualTo(AmountClarify.QUESTION_FR);
        assertThat(billing.calls).isZero();
    }

    @Test
    void a_vague_follow_up_asks_a_second_question() {
        // GIVEN the assistant already asked which amount or service
        var memory = new Memory();
        memory.turns.add(new ConversationTurn("ma facture a augmenté", AmountClarify.QUESTION_FR));
        var router = new BillingRoutingService(billing, transcript -> true, memory);
        var turn = new RoutableTurn("je ne comprends pas", "conv-1", "fr", "99224964", "web_voice", "corr-2");

        // WHEN the customer stays vague
        Optional<GeneratedAnswer> answer = router.billingAnswer(turn);

        // THEN a second, more precise question is asked
        assertThat(answer).isPresent();
        assertThat(answer.get().text()).isEqualTo(AmountClarify.FOLLOW_UP_FR);
        assertThat(billing.calls).isZero();
    }

    @Test
    void the_clarify_budget_hands_off_instead_of_reciting() {
        // GIVEN two clarification questions were already asked
        var memory = new Memory();
        memory.turns.add(new ConversationTurn("ma facture a augmenté", AmountClarify.QUESTION_FR));
        memory.turns.add(new ConversationTurn("je ne comprends pas", AmountClarify.FOLLOW_UP_FR));
        var router = new BillingRoutingService(billing, transcript -> true, memory);
        var turn = new RoutableTurn("je ne sais pas", "conv-1", "fr", "99224964", "web_voice", "corr-3");

        // WHEN the customer is still vague
        Optional<GeneratedAnswer> answer = router.billingAnswer(turn);

        // THEN the assistant hands off and does not recite the bill
        assertThat(answer).isPresent();
        assertThat(answer.get().requiresEscalation()).isTrue();
        assertThat(billing.calls).isZero();
    }

    @Test
    void a_figure_after_the_question_is_sent_to_billing() {
        // GIVEN the assistant just asked which amount
        var memory = new Memory();
        memory.turns.add(new ConversationTurn(
                "je ne comprends pas un montant sur ma facture", AmountClarify.QUESTION_FR));
        var router = new BillingRoutingService(billing, transcript -> false, memory);
        var turn = new RoutableTurn("le 29,99", "conv-1", "fr", "99224964", "web_voice", "corr-2");

        // WHEN the customer names the amount
        Optional<GeneratedAnswer> answer = router.billingAnswer(turn);

        // THEN that amount is explained by the billing chain
        assertThat(answer).isPresent();
        assertThat(billing.calls).isEqualTo(1);
        assertThat(billing.lastRequest.transcript()).isEqualTo("le 29,99");
    }

    @Test
    void returns_empty_when_account_reference_present_but_not_a_billing_question() {
        var router = new BillingRoutingService(billing, transcript -> false);
        var turn = new RoutableTurn("quels sont vos horaires ?", "conv-1", "fr", "99224964", "web_voice", "corr-1");

        assertThat(router.billingAnswer(turn)).isEmpty();
        assertThat(billing.calls).isZero();
    }

    private static final class Memory implements ConversationMemoryPort {
        private final List<ConversationTurn> turns = new ArrayList<>();

        @Override
        public List<ConversationTurn> recentTurns(String conversationId) {
            return List.copyOf(turns);
        }

        @Override
        public void append(String conversationId, ConversationTurn turn) {
            turns.add(turn);
        }
    }

    private static final class RecordingBilling implements AnswerBillingQuestionUseCase {
        private int calls;
        private BillingExplanationRequest lastRequest;

        @Override
        public GeneratedAnswer answer(BillingExplanationRequest request) {
            this.calls++;
            this.lastRequest = request;
            return GeneratedAnswer.grounded("billing answer", 0.9);
        }
    }
}
