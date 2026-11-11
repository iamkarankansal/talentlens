package com.talentlens.candidate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Candidate details sent by clients. Values are normalised before validation runs: the name and
 * phone are trimmed (a blank phone becomes null) and the email is trimmed and lower-cased.
 */
public record CandidateRequest(
        @NotBlank(message = "fullName is required")
        @Size(max = 255, message = "fullName must be at most 255 characters")
        String fullName,

        @NotBlank(message = "email is required")
        @Email(message = "email must be valid")
        @Size(max = 254, message = "email must be at most 254 characters")
        String email,

        @Size(max = 30, message = "phone must be at most 30 characters")
        @Pattern(regexp = "^\\+?[(]?(?:[0-9][ ().-]*){6,14}[0-9]$",
                message = "phone must have 7 to 15 digits, optionally starting with + and separated by spaces, dots, dashes or brackets")
        String phone,

        @Size(max = 50000, message = "resumeText must be at most 50000 characters")
        String resumeText) {

    public CandidateRequest {
        fullName = fullName == null ? null : fullName.trim();
        email = email == null ? null : email.trim().toLowerCase();
        phone = phone == null || phone.isBlank() ? null : phone.trim();
    }
}
