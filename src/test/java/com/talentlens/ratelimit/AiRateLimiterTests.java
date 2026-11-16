package com.talentlens.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.talentlens.common.TooManyRequestsException;
import org.junit.jupiter.api.Test;

class AiRateLimiterTests {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-16T10:00:00Z"));
    private final AiRateLimiter limiter =
            new AiRateLimiter(new AiRateLimitProperties(2, Duration.ofMinutes(1)), clock);

    @Test
    void refusesRequestsBeyondTheAllowanceUntilTheWindowEnds() {
        limiter.acquire("10.0.0.1");
        clock.advance(Duration.ofSeconds(20));
        limiter.acquire("10.0.0.1");

        assertThatThrownBy(() -> limiter.acquire("10.0.0.1"))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        ex -> assertThat(ex.getRetryAfterSeconds()).isBetween(40L, 41L));

        clock.advance(Duration.ofSeconds(40));
        assertThatCode(() -> limiter.acquire("10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void countsEachClientSeparately() {
        limiter.acquire("10.0.0.1");
        limiter.acquire("10.0.0.1");

        assertThatCode(() -> limiter.acquire("10.0.0.2")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.acquire("10.0.0.1")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void zeroRequestsTurnsTheLimitOff() {
        AiRateLimiter off = new AiRateLimiter(new AiRateLimitProperties(0, Duration.ofMinutes(1)), clock);

        assertThat(off.enabled()).isFalse();
        for (int i = 0; i < 100; i++) {
            off.acquire("10.0.0.1");
        }
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
