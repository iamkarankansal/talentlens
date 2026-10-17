package com.talentlens.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new account. Passwords are limited to 72 characters because BCrypt ignores anything longer.
 */
public record RegisterUserRequest(
        @NotBlank(message = "email is required") @Email(message = "email must be valid") @Size(max = 255)
        String email,
        @NotBlank(message = "fullName is required") @Size(max = 255) String fullName,
        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "password must be between 8 and 72 characters") String password) {
}
