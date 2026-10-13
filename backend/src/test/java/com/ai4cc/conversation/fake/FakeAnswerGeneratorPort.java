package com.ai4cc.conversation.fake;

import com.ai4cc.conversation.domain.model.valueobject.AnswerLanguage;
import com.ai4cc.conversation.domain.model.valueobject.RetrievedEvidence;
import com.ai4cc.conversation.domain.port.out.AnswerGeneratorPort;

import java.util.List;

public class FakeAnswerGeneratorPort implements AnswerGeneratorPort {

    private String nextAnswer = "Réponse générée à partir du contexte.";

    public String lastQuestion;
    public List<RetrievedEvidence> lastEvidence;
    public List<String> lastHistory;
    public AnswerLanguage lastLanguage;
    public int callCount;

    public void setNextAnswer(String nextAnswer) {
        this.nextAnswer = nextAnswer;
    }

    @Override
    public String generate(
            String question, List<RetrievedEvidence> evidence, List<String> history, AnswerLanguage language) {
        this.lastQuestion = question;
        this.lastEvidence = evidence;
        this.lastHistory = history;
        this.lastLanguage = language;
        this.callCount++;
        return nextAnswer;
    }
}
