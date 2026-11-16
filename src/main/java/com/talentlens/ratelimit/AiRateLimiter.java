package com.talentlens.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.talentlens.common.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Counts AI requests per client in fixed windows kept in memory. Once a client has made {@code requests} calls in
 * the current window, further calls are refused with {@code 429} until the window ends. The state is per instance
 * and is lost on restart.
 */
@Component
public class AiRateLimiter {

    private static final int PRUNE_THRESHOLD = 10_000;

    private final int requests;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public AiRateLimiter(AiRateLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    AiRateLimiter(AiRateLimitProperties properties, Clock clock) {
        this.requests = properties.requests();
        this.window = properties.window();
        this.clock = clock;
    }

    public boolean enabled() {
        return requests > 0 && window != null && !window.isZero() && !window.isNegative();
    }

    /** Counts one request for the client, or fails with {@code 429} when its allowance for the window is used up. */
    public void acquire(String client) {
        if (!enabled()) {
            return;
        }
        Instant now = clock.instant();
        if (windows.size() > PRUNE_THRESHOLD) {
            windows.values().removeIf(entry -> entry.hasEnded(now, window));
        }
        Window current = windows.compute(client, (key, previous) -> {
            if (previous == null || previous.hasEnded(now, window)) {
                return new Window(now, 1);
            }
            return new Window(previous.start(), previous.count() + 1);
        });
        if (current.count() > requests) {
            Instant end = current.start().plus(window);
            long seconds = Duration.between(now, end).toSeconds() + 1;
            throw new TooManyRequestsException("Too many AI requests, try again later", seconds);
        }
    }

    private record Window(Instant start, int count) {

        boolean hasEnded(Instant now, Duration window) {
            return !now.isBefore(start.plus(window));
        }
    }
}
