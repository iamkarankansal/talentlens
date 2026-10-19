package com.talentlens.user;

import java.time.Instant;

/**
 * A user account as returned by the API. The password hash is never included.
 */
public record UserResponse(Long id, String email, String fullName, Role role, Instant createdAt) {

    public static UserResponse from(UserAccount user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                user.getCreatedAt());
    }
}
