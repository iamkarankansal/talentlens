package com.talentlens.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for signing access tokens. The secret must be at least 32 bytes for HS256; when it is empty a random
 * key is generated at startup, so tokens do not survive a restart.
 */
@ConfigurationProperties(prefix = "talentlens.security.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("1h") Duration expiration,
        @DefaultValue("talentlens") String issuer) {
}
