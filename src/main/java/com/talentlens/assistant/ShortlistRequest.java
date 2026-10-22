package com.talentlens.assistant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ShortlistRequest(
        @NotNull(message = "jobId is required") Long jobId,
        @Min(value = 1, message = "size must be between 1 and 20")
        @Max(value = 20, message = "size must be between 1 and 20") Integer size) {
}
