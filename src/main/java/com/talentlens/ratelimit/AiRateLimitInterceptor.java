package com.talentlens.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Applies the AI rate limit to the endpoints it is registered for, keyed by the client's remote address.
 */
public class AiRateLimitInterceptor implements HandlerInterceptor {

    private final AiRateLimiter limiter;

    public AiRateLimitInterceptor(AiRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod) {
            limiter.acquire(request.getRemoteAddr());
        }
        return true;
    }
}
