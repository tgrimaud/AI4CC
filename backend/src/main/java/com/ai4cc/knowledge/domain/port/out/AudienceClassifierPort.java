package com.ai4cc.knowledge.domain.port.out;

public interface AudienceClassifierPort {

    String classify(String title, String content);
}
