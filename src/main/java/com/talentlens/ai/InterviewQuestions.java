package com.talentlens.ai;

import java.util.List;

/**
 * Interview questions written by the language model for one candidate and job. Each question has the
 * job requirement or candidate claim it probes ({@code focus}) and why it is worth asking ({@code rationale}).
 */
public record InterviewQuestions(List<Question> questions) {

    public record Question(String question, String focus, String rationale) {
    }
}
