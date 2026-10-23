package com.talentlens.assistant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EmailDraftRequest(
        @NotNull(message = "applicationId is required") Long applicationId,
        @NotNull(message = "type is required") EmailType type,
        @Size(max = 500, message = "note must be at most 500 characters") String note) {
}
