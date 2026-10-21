package com.talentlens.assistant;

import java.util.List;

import com.talentlens.ai.InterviewQuestions;

public record InterviewQuestionsResponse(Long jobId, Long candidateId, String fullName, String jobTitle,
                                         List<InterviewQuestions.Question> questions) {
}
