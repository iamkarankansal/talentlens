package com.talentlens.job;

import jakarta.validation.constraints.NotNull;

public record JobStatusRequest(@NotNull(message = "status is required") JobStatus status) {
}
