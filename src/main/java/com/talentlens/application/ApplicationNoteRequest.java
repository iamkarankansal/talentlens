package com.talentlens.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApplicationNoteRequest(
        @NotBlank(message = "body is required")
        @Size(max = 2000, message = "body must be at most 2000 characters") String body,
        @Size(max = 100, message = "author must be at most 100 characters") String author) {
}
