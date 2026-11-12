package com.talentlens.config;

import com.talentlens.auth.AccessControlInterceptor;
import com.talentlens.auth.AuthService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Turns on role checks for write and AI endpoints when {@code AUTH_ENFORCED=true}. Off by default, so existing
 * clients keep working until they send tokens.
 */
@Configuration
@ConditionalOnProperty(prefix = "talentlens.security", name = "auth-enforced", havingValue = "true")
public class AccessControlConfig implements WebMvcConfigurer {

    private final AuthService authService;

    public AccessControlConfig(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AccessControlInterceptor(authService)).addPathPatterns("/api/v1/**");
    }
}
