package org.ozonLabel.ozonApi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

/**
 * CSRF Validation Filter using Double-Submit Cookie Pattern
 *
 * Validates that the CSRF token in the X-XSRF-TOKEN header matches
 * the token stored in the XSRF-TOKEN cookie.
 *
 * This pattern is secure because:
 * - Cookies with SameSite=Strict cannot be read from other domains
 * - Headers can only be set by JavaScript from the same origin
 * - If both match, the request is from a legitimate source
 */
@Component
@Slf4j
public class CsrfValidationFilter extends OncePerRequestFilter {

    private static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Skip CSRF validation for safe (read-only) methods
        if (SAFE_METHODS.contains(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip CSRF for public file uploads path (if needed for specific cases)
        String path = request.getRequestURI();
        if (path.startsWith("/uploads/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Get authentication - only validate CSRF for authenticated requests
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract CSRF token from header and cookie
        String csrfTokenFromHeader = request.getHeader(CSRF_HEADER);
        String csrfTokenFromCookie = extractCsrfTokenFromCookie(request);

        // Validate: header must be present
        if (csrfTokenFromHeader == null || csrfTokenFromHeader.trim().isEmpty()) {
            log.warn("CSRF token missing in header for user: {} on path: {}",
                    auth.getName(), request.getRequestURI());
            sendCsrfError(response, "CSRF token is required");
            return;
        }

        // Validate: cookie must be present
        if (csrfTokenFromCookie == null || csrfTokenFromCookie.trim().isEmpty()) {
            log.warn("CSRF token missing in cookie for user: {} on path: {}",
                    auth.getName(), request.getRequestURI());
            sendCsrfError(response, "CSRF cookie not found");
            return;
        }

        // Validate: header must match cookie (Double-Submit Cookie Pattern)
        if (!csrfTokenFromHeader.equals(csrfTokenFromCookie)) {
            log.warn("CSRF token mismatch for user: {} on path: {}. Header and cookie don't match.",
                    auth.getName(), request.getRequestURI());
            sendCsrfError(response, "Invalid CSRF token");
            return;
        }

        // Validation passed
        filterChain.doFilter(request, response);
    }

    private String extractCsrfTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        return Arrays.stream(cookies)
                .filter(cookie -> CSRF_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isEmpty())
                .findFirst()
                .orElse(null);
    }

    private void sendCsrfError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(String.format(
                "{\"success\":false,\"message\":\"%s\",\"error\":\"CSRF_VALIDATION_FAILED\"}",
                message
        ));
    }
}
