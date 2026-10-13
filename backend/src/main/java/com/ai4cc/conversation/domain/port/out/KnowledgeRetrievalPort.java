package com.ai4cc.conversation.domain.port.out;

import com.ai4cc.conversation.domain.model.valueobject.RetrievedEvidence;
import java.util.List;

public interface KnowledgeRetrievalPort {

    List<RetrievedEvidence> retrieve(String query, String domain, String language, int topK);
}
