package com.ai4cc.conversation.fake;

import com.ai4cc.conversation.domain.model.valueobject.RetrievedEvidence;
import com.ai4cc.conversation.domain.port.out.KnowledgeRetrievalPort;

import java.util.ArrayList;
import java.util.List;

public class FakeKnowledgeRetrievalPort implements KnowledgeRetrievalPort {

    private final List<RetrievedEvidence> evidence = new ArrayList<>();

    public String lastQuery;
    public String lastDomain;
    public String lastLanguage;
    public int lastTopK;
    public int callCount;

    public void setEvidence(List<RetrievedEvidence> evidence) {
        this.evidence.clear();
        this.evidence.addAll(evidence);
    }

    @Override
    public List<RetrievedEvidence> retrieve(String query, String domain, String language, int topK) {
        this.lastQuery = query;
        this.lastDomain = domain;
        this.lastLanguage = language;
        this.lastTopK = topK;
        this.callCount++;
        return List.copyOf(evidence);
    }
}
