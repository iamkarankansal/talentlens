package com.talentlens.application;

import jakarta.validation.constraints.NotNull;

public record ApplicationStatusRequest(@NotNull(message = "status is required") ApplicationStatus status) {
}
