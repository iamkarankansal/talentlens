package com.talentlens.auth;

/**
 * An access token to send as {@code Authorization: Bearer <accessToken>}.
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
