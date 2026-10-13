package com.ai4cc.knowledge.domain.port.in;

import com.ai4cc.knowledge.domain.model.valueobject.KnowledgeChunk;
import java.util.List;

public interface KnowledgeRetrievalUseCase {

    List<KnowledgeChunk> retrieve(String query, String domain, String language, int topK);
}
