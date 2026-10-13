package com.talentlens.application;

import jakarta.validation.constraints.NotNull;

public record ApplicationRequest(
        @NotNull(message = "candidateId is required") Long candidateId,
        @NotNull(message = "jobId is required") Long jobId) {
}
