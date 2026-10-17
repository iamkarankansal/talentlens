package com.talentlens.user;

import java.time.Instant;

/**
 * A user account as returned by the API. The password hash is never included.
 */
public record UserResponse(Long id, String email, String fullName, Instant createdAt) {

    static UserResponse from(UserAccount user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getCreatedAt());
    }
}
