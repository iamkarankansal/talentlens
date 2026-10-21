package com.talentlens.ai;

public interface InterviewQuestionGenerator {

    InterviewQuestions generate(String jobText, String candidateText, int count);
}
