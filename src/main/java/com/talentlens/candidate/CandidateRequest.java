package com.talentlens.candidate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CandidateRequest(
        @NotBlank(message = "fullName is required") @Size(max = 255) String fullName,
        @NotBlank(message = "email is required") @Email(message = "email must be valid") String email,
        @Size(max = 30) String phone,
        @Size(max = 50000) String resumeText) {
}
