package com.talentlens.auth;

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

class LoginThrottleTests {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-14T10:00:00Z"));
    private final LoginThrottle throttle =
            new LoginThrottle(new LoginThrottleProperties(2, Duration.ofMinutes(15)), clock);

    @Test
    void locksUntilTheLockoutPeriodHasPassed() {
        throttle.recordFailure("a@example.com");
        assertThatCode(() -> throttle.checkAllowed("a@example.com")).doesNotThrowAnyException();
        throttle.recordFailure("a@example.com");

        assertThatThrownBy(() -> throttle.checkAllowed("a@example.com"))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        ex -> assertThat(ex.getRetryAfterSeconds()).isBetween(899L, 901L));

        clock.advance(Duration.ofMinutes(10));
        assertThatThrownBy(() -> throttle.checkAllowed("a@example.com"))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        ex -> assertThat(ex.getRetryAfterSeconds()).isBetween(299L, 301L));

        clock.advance(Duration.ofMinutes(5));
        assertThatCode(() -> throttle.checkAllowed("a@example.com")).doesNotThrowAnyException();

        // The count starts again after the lockout.
        throttle.recordFailure("a@example.com");
        assertThatCode(() -> throttle.checkAllowed("a@example.com")).doesNotThrowAnyException();
    }

    @Test
    void forgetsOldFailures() {
        throttle.recordFailure("b@example.com");
        clock.advance(Duration.ofMinutes(16));
        throttle.recordFailure("b@example.com");

        assertThatCode(() -> throttle.checkAllowed("b@example.com")).doesNotThrowAnyException();
    }

    @Test
    void canBeTurnedOff() {
        LoginThrottle off = new LoginThrottle(new LoginThrottleProperties(0, Duration.ofMinutes(15)), clock);
        for (int i = 0; i < 10; i++) {
            off.recordFailure("c@example.com");
        }
        assertThatCode(() -> off.checkAllowed("c@example.com")).doesNotThrowAnyException();
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
