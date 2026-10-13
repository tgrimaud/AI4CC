package com.ai4cc.knowledge.domain.port.in;

import com.ai4cc.knowledge.domain.model.valueobject.SyncReport;

public interface SyncKnowledgeUseCase {

    SyncReport syncAll();

    SyncReport sync(String sourceType);
}
