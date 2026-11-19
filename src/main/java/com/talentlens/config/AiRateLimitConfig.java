package com.talentlens.config;

import com.talentlens.ratelimit.AiRateLimitInterceptor;
import com.talentlens.ratelimit.AiRateLimitProperties;
import com.talentlens.ratelimit.AiRateLimiter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Puts the per-client rate limit in front of every endpoint that calls the model or the embedding store.
 */
@Configuration
@EnableConfigurationProperties(AiRateLimitProperties.class)
public class AiRateLimitConfig implements WebMvcConfigurer {

    static final String[] AI_PATHS = {
            "/api/v1/assistant/**",
            "/api/v1/jobs/*/matches",
            "/api/v1/jobs/*/matches/**",
            "/api/v1/jobs/*/embedding",
            "/api/v1/candidates/*/embedding",
            "/api/v1/candidates/embeddings",
            "/api/v1/candidates/*/resume/parse"
    };

    private final AiRateLimiter limiter;

    public AiRateLimitConfig(AiRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (limiter.enabled()) {
            registry.addInterceptor(new AiRateLimitInterceptor(limiter)).addPathPatterns(AI_PATHS);
        }
    }
}
