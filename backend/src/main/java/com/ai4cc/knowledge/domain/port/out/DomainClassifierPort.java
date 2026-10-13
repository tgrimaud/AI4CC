package com.ai4cc.knowledge.domain.port.out;

public interface DomainClassifierPort {

    String classify(String title, String content);
}
