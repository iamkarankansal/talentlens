package com.talentlens.ai;

public interface CandidatePoolAssistant {

    /**
     * Answers a recruiter's question using only the candidate profiles given as context.
     */
    String answer(String question, String candidateContext);
}
