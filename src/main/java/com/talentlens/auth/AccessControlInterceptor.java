package com.talentlens.auth;

import java.util.List;
import java.util.Set;

import com.talentlens.user.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Requires a {@code RECRUITER} or {@code ADMIN} bearer token for every write request and every AI endpoint under
 * {@code /api/v1}. Logging in and registering stay open, and so do plain reads. Only registered when
 * {@code talentlens.security.auth-enforced=true}.
 */
public class AccessControlInterceptor implements HandlerInterceptor {

    private static final String API_PREFIX = "/api/v1/";

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    /** Paths anyone may call, whatever the method. */
    private static final List<String> OPEN_PATHS = List.of("/api/v1/auth/**", "/api/v1/users");

    /** Endpoints that call the model or the embedding store, including the read-only ones. */
    private static final List<String> AI_PATHS = List.of("/api/v1/assistant/**", "/api/v1/jobs/*/matches",
            "/api/v1/jobs/*/matches/**");

    private final AuthService authService;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public AccessControlInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (requiresStaff(request.getMethod(), path)) {
            authService.requireRole(request.getHeader(HttpHeaders.AUTHORIZATION), Role.RECRUITER, Role.ADMIN);
        }
        return true;
    }

    boolean requiresStaff(String method, String path) {
        if (!path.startsWith(API_PREFIX) || matchesAny(OPEN_PATHS, path)) {
            return false;
        }
        return WRITE_METHODS.contains(method) || matchesAny(AI_PATHS, path);
    }

    private boolean matchesAny(List<String> patterns, String path) {
        return patterns.stream().anyMatch(pattern -> matcher.match(pattern, path));
    }
}
