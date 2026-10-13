package com.ai4cc.conversation.fake;

import com.ai4cc.knowledge.domain.model.valueobject.KnowledgeChunk;
import com.ai4cc.knowledge.domain.port.in.KnowledgeRetrievalUseCase;
import java.util.ArrayList;
import java.util.List;

public class FakeKnowledgeRetrievalUseCase implements KnowledgeRetrievalUseCase {

    private final List<KnowledgeChunk> chunks = new ArrayList<>();

    public void setChunks(List<KnowledgeChunk> chunks) {
        this.chunks.clear();
        this.chunks.addAll(chunks);
    }

    @Override
    public List<KnowledgeChunk> retrieve(String query, String domain, String language, int topK) {
        return chunks.stream().limit(topK).toList();
    }
}
