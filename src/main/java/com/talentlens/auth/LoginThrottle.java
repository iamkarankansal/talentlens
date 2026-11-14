package com.talentlens.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.talentlens.common.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Counts failed logins per email in memory. After {@code maxAttempts} failures in a row the email is locked for the
 * lockout period, during which every login for it is refused, even with the right password. Failures older than
 * the lockout period are forgotten, and a successful login clears the count. The state is per instance and is lost
 * on restart.
 */
@Component
public class LoginThrottle {

    private static final int PRUNE_THRESHOLD = 10_000;

    private final int maxAttempts;
    private final Duration lockout;
    private final Clock clock;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    @Autowired
    public LoginThrottle(LoginThrottleProperties properties) {
        this(properties, Clock.systemUTC());
    }

    LoginThrottle(LoginThrottleProperties properties, Clock clock) {
        this.maxAttempts = properties.maxAttempts();
        this.lockout = properties.lockout();
        this.clock = clock;
    }

    /** Fails with {@code 429} while the email is locked out. */
    public void checkAllowed(String email) {
        if (!enabled()) {
            return;
        }
        Attempts current = attempts.get(email);
        if (current == null || current.lockedUntil() == null) {
            return;
        }
        Instant now = clock.instant();
        if (now.isBefore(current.lockedUntil())) {
            long seconds = Duration.between(now, current.lockedUntil()).toSeconds() + 1;
            throw new TooManyRequestsException("Too many failed logins for this email, try again later", seconds);
        }
        attempts.remove(email, current);
    }

    public void recordFailure(String email) {
        if (!enabled()) {
            return;
        }
        Instant now = clock.instant();
        if (attempts.size() > PRUNE_THRESHOLD) {
            attempts.values().removeIf(entry -> entry.isStale(now, lockout));
        }
        attempts.compute(email, (key, previous) -> {
            int failures = previous == null || previous.isStale(now, lockout) ? 1 : previous.failures() + 1;
            Instant lockedUntil = failures >= maxAttempts ? now.plus(lockout) : null;
            return new Attempts(failures, now, lockedUntil);
        });
    }

    public void recordSuccess(String email) {
        attempts.remove(email);
    }

    private boolean enabled() {
        return maxAttempts > 0 && lockout != null && !lockout.isZero() && !lockout.isNegative();
    }

    private record Attempts(int failures, Instant lastFailure, Instant lockedUntil) {

        boolean isStale(Instant now, Duration lockout) {
            Instant expiry = lockedUntil != null ? lockedUntil : lastFailure.plus(lockout);
            return !now.isBefore(expiry);
        }
    }
}
