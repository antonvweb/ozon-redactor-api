package org.ozonLabel.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.auth.service.CookieService;
import org.ozonLabel.auth.service.CsrfTokenService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * CSRF Validation Filter
 * Validates CSRF token for state-changing requests (POST, PUT, DELETE, PATCH)
 * Uses double-submit cookie pattern
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CsrfValidationFilter extends OncePerRequestFilter {

    private final CsrfTokenService csrfTokenService;
    private final CookieService cookieService;

    private static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final Set<String> CSRF_EXEMPT_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/request-code",
            "/api/auth/create-account",
            "/api/auth/refresh-token"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Skip CSRF validation for safe methods
        if (SAFE_METHODS.contains(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip CSRF validation for exempt paths (login, registration)
        String path = request.getRequestURI();
        if (CSRF_EXEMPT_PATHS.stream().anyMatch(path::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Get authentication
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            // No authentication, skip CSRF (will fail on auth anyway)
            filterChain.doFilter(request, response);
            return;
        }

        // Validate CSRF token
        String csrfTokenFromHeader = request.getHeader(CSRF_HEADER);
        String csrfTokenFromCookie = cookieService.extractCsrfToken(request).orElse(null);
        String userEmail = auth.getName();

        // Double-submit validation: header must match cookie AND stored token
        if (csrfTokenFromHeader == null || csrfTokenFromHeader.isEmpty()) {
            log.warn("CSRF token missing in header for user: {}", userEmail);
            sendCsrfError(response, "CSRF token отсутствует");
            return;
        }

        if (csrfTokenFromCookie == null || !csrfTokenFromHeader.equals(csrfTokenFromCookie)) {
            log.warn("CSRF token mismatch (header vs cookie) for user: {}", userEmail);
            sendCsrfError(response, "Недействительный CSRF token");
            return;
        }

        if (!csrfTokenService.validateToken(userEmail, csrfTokenFromHeader)) {
            log.warn("CSRF token validation failed for user: {}", userEmail);
            sendCsrfError(response, "CSRF token не прошёл проверку");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendCsrfError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(String.format(
                "{\"success\":false,\"message\":\"%s\",\"error\":\"CSRF_ERROR\"}",
                message
        ));
    }
}
