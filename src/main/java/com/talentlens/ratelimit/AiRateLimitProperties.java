package com.talentlens.ratelimit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limits on how often one client may call the AI endpoints. {@code requests} of zero or less turns the limit off.
 */
@ConfigurationProperties(prefix = "talentlens.ai.rate-limit")
public record AiRateLimitProperties(
        @DefaultValue("0") int requests,
        @DefaultValue("1m") Duration window) {
}
