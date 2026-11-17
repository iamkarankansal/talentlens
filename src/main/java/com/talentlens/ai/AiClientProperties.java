package com.talentlens.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How calls to the model provider are retried and how long they may take. Retries wait {@code initialBackoff},
 * then multiply the wait by {@code backoffMultiplier} up to {@code maxBackoff}. {@code maxAttempts} counts the
 * first call, so 1 turns retries off.
 */
@ConfigurationProperties(prefix = "talentlens.ai.client")
public record AiClientProperties(
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("2s") Duration initialBackoff,
        @DefaultValue("2.0") double backoffMultiplier,
        @DefaultValue("30s") Duration maxBackoff,
        @DefaultValue("10s") Duration connectTimeout,
        @DefaultValue("60s") Duration readTimeout) {

    public AiClientProperties {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("talentlens.ai.client.max-attempts must be at least 1");
        }
        if (initialBackoff.toMillis() < 1) {
            throw new IllegalArgumentException("talentlens.ai.client.initial-backoff must be at least 1ms");
        }
        if (backoffMultiplier <= 1.0) {
            throw new IllegalArgumentException("talentlens.ai.client.backoff-multiplier must be greater than 1");
        }
        if (maxBackoff.compareTo(initialBackoff) <= 0) {
            throw new IllegalArgumentException("talentlens.ai.client.max-backoff must be longer than initial-backoff");
        }
        if (connectTimeout.isZero() || connectTimeout.isNegative()
                || readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException("talentlens.ai.client timeouts must be positive");
        }
    }
}
