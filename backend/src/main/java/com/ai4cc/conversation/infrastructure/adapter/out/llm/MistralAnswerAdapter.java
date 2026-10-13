package com.ai4cc.conversation.infrastructure.adapter.out.llm;

import com.ai4cc.shared.observability.BackendTelemetry;
import org.springframework.ai.chat.client.ChatClient;

// Mistral wording adapter (mistral-small-latest), a selectable provider (DEC-011); the default is
// OpenAI since ADR-0051. Mistral stays the explicitly pinned pilot provider until the ADR-0045 benchmark.
public class MistralAnswerAdapter extends AbstractChatClientAnswerAdapter {

    private static final String PROVIDER = "mistral-api";

    // System prompt is the shared DEC-002 voice prompt in the base class (TASK-BE-053).

    public MistralAnswerAdapter(
            ChatClient chatClient, BackendTelemetry telemetry, long timeoutMs,
            long streamTimeoutMs, int maxAnswerSentences) {
        super(chatClient, telemetry, timeoutMs, streamTimeoutMs, maxAnswerSentences);
    }

    @Override
    protected String providerName() {
        return PROVIDER;
    }
}
