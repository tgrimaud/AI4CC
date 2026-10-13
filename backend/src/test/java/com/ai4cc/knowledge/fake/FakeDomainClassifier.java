package com.ai4cc.knowledge.fake;

import com.ai4cc.knowledge.domain.port.out.DomainClassifierPort;

import java.util.ArrayList;
import java.util.List;

public class FakeDomainClassifier implements DomainClassifierPort {

    public String returns = "general";
    public final List<String> classifiedTitles = new ArrayList<>();
    public String lastContent;

    @Override
    public String classify(String title, String content) {
        classifiedTitles.add(title);
        lastContent = content;
        return returns;
    }
}
