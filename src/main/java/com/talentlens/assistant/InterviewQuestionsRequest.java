package com.talentlens.assistant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record InterviewQuestionsRequest(
        @NotNull(message = "jobId is required") Long jobId,
        @NotNull(message = "candidateId is required") Long candidateId,
        @Min(value = 1, message = "count must be between 1 and 15")
        @Max(value = 15, message = "count must be between 1 and 15") Integer count) {
}
