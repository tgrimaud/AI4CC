package com.ai4cc.knowledge.domain.port.out;

import com.ai4cc.knowledge.domain.model.valueobject.SourceDocument;
import java.util.List;

public interface KnowledgeSourceConnector {

    String sourceType();

    List<SourceDocument> fetchAll();
}
