package com.ai4cc.conversation.application.service;

import com.ai4cc.conversation.domain.model.valueobject.AnswerLanguage;
import com.ai4cc.conversation.domain.model.valueobject.ConversationTurn;
import com.ai4cc.conversation.domain.model.valueobject.GeneratedAnswer;
import com.ai4cc.conversation.domain.model.valueobject.GuardrailDecision;
import com.ai4cc.conversation.domain.model.valueobject.RoutableTurn;
import com.ai4cc.conversation.domain.port.in.AnswerBillingQuestionUseCase;
import com.ai4cc.conversation.domain.port.out.BillingIntentPort;
import com.ai4cc.conversation.domain.port.out.ConversationMemoryPort;
import com.ai4cc.conversation.domain.service.AmountClarify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

// Single source of truth for the RAG-vs-billing routing decision (TASK-BE-061 / BUG-027, ADR-0055),
// shared by the blocking /converse path and the streaming path. A billing turn with an account
// reference is a clarification until the customer names an amount or a service (BUG-031). The
// number of questions is the existing billing-clarify budget; past it, the turn is handed to an
// advisor instead of reciting the bill.
public class BillingRoutingService {

    private static final int DEFAULT_MAX_QUESTIONS = 2;

    private final AnswerBillingQuestionUseCase billing;
    private final BillingIntentPort billingIntent;
    private final ConversationMemoryPort memory;
    private final int maxQuestions;

    public BillingRoutingService(AnswerBillingQuestionUseCase billing, BillingIntentPort billingIntent) {
        this(billing, billingIntent, null, DEFAULT_MAX_QUESTIONS);
    }

    public BillingRoutingService(
            AnswerBillingQuestionUseCase billing,
            BillingIntentPort billingIntent,
            ConversationMemoryPort memory) {
        this(billing, billingIntent, memory, DEFAULT_MAX_QUESTIONS);
    }

    public BillingRoutingService(
            AnswerBillingQuestionUseCase billing,
            BillingIntentPort billingIntent,
            ConversationMemoryPort memory,
            int maxQuestions) {
        this.billing = billing;
        this.billingIntent = billingIntent;
        this.memory = memory == null ? NONE : memory;
        this.maxQuestions = maxQuestions;
    }

    public Optional<GeneratedAnswer> billingAnswer(RoutableTurn turn) {
        if (!turn.hasAccountReference() || !billingTurn(turn)) {
            return Optional.empty();
        }
        if (maxQuestions <= 0 || AmountClarify.hasConcreteFocus(turn.transcript())) {
            return Optional.of(billing.answer(turn.toBillingExplanationRequest()));
        }
        int asked = questionsAlreadyAsked(turn);
        if (asked >= maxQuestions) {
            return Optional.of(handOff(turn));
        }
        return Optional.of(ask(turn, asked));
    }

    private boolean billingTurn(RoutableTurn turn) {
        return billingIntent.isBillingQuestion(turn.transcript()) || questionsAlreadyAsked(turn) > 0;
    }

    private int questionsAlreadyAsked(RoutableTurn turn) {
        List<ConversationTurn> newestFirst = new ArrayList<>(memory.recentTurns(turn.conversationKey()));
        Collections.reverse(newestFirst);
        int streak = 0;
        for (ConversationTurn prior : newestFirst) {
            if (!AmountClarify.isQuestion(prior.assistantText())) {
                break;
            }
            streak++;
        }
        return streak;
    }

    private GeneratedAnswer ask(RoutableTurn turn, int alreadyAsked) {
        String question = AmountClarify.question(turn.forcedLanguage(), turn.transcript(), alreadyAsked);
        memory.append(turn.conversationKey(), new ConversationTurn(turn.transcript(), question));
        return GeneratedAnswer.fallback(question, GuardrailDecision.Verdict.CLARIFY);
    }

    private GeneratedAnswer handOff(RoutableTurn turn) {
        return GeneratedAnswer.fallback(
                languageOf(turn).handoffSentence(), GuardrailDecision.Verdict.LOW_CONFIDENCE);
    }

    private static AnswerLanguage languageOf(RoutableTurn turn) {
        if (turn.forcedLanguage() != null && !turn.forcedLanguage().isBlank()) {
            return AnswerLanguage.fromCode(turn.forcedLanguage());
        }
        String text = turn.transcript() == null ? "" : turn.transcript().toLowerCase(Locale.ROOT);
        return text.contains("facture") || text.contains("montant")
                ? AnswerLanguage.FRENCH : AnswerLanguage.ENGLISH;
    }

    private static final ConversationMemoryPort NONE = new ConversationMemoryPort() {
        @Override
        public List<ConversationTurn> recentTurns(String conversationId) {
            return List.of();
        }

        @Override
        public void append(String conversationId, ConversationTurn turn) {
        }
    };
}
