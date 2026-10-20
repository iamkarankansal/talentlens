package com.talentlens.assistant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank(message = "question is required")
        @Size(max = 2000, message = "question must be at most 2000 characters") String question,
        @Min(value = 1, message = "topK must be between 1 and 20")
        @Max(value = 20, message = "topK must be between 1 and 20") Integer topK) {
}
