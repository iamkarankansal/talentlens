package com.talentlens.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for locking out an email after repeated failed logins. {@code maxAttempts} of zero or less turns the
 * throttle off.
 */
@ConfigurationProperties(prefix = "talentlens.security.login-throttle")
public record LoginThrottleProperties(
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("15m") Duration lockout) {
}
