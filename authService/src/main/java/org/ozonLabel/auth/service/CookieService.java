package org.ozonLabel.auth.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Service for secure cookie management
 * Implements HTTP-only, Secure, SameSite cookies for JWT tokens
 */
@Service
public class CookieService {

    private static final String ACCESS_TOKEN_COOKIE = "accessToken";
    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    private static final String CSRF_TOKEN_COOKIE = "XSRF-TOKEN";

    @Value("${cookie.secure:true}")
    private boolean secureCookie;

    @Value("${cookie.domain:}")
    private String cookieDomain;

    @Value("${jwt.expiration:86400000}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-expiration:1209600000}")
    private long refreshTokenExpiration;

    /**
     * Creates secure HTTP-only cookie for access token
     */
    public ResponseCookie createAccessTokenCookie(String token) {
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(Duration.ofMillis(accessTokenExpiration))
                .sameSite("Strict")
                .domain(cookieDomain.isEmpty() ? null : cookieDomain)
                .build();
    }

    /**
     * Creates secure HTTP-only cookie for refresh token
     */
    public ResponseCookie createRefreshTokenCookie(String token) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(secureCookie)
                .path("/api/auth/refresh-token") // Only sent to refresh endpoint
                .maxAge(Duration.ofMillis(refreshTokenExpiration))
                .sameSite("Strict")
                .domain(cookieDomain.isEmpty() ? null : cookieDomain)
                .build();
    }

    /**
     * Creates CSRF token cookie (readable by JavaScript for double-submit)
     */
    public ResponseCookie createCsrfTokenCookie(String token) {
        return ResponseCookie.from(CSRF_TOKEN_COOKIE, token)
                .httpOnly(false) // Must be readable by JavaScript
                .secure(secureCookie)
                .path("/")
                .maxAge(Duration.ofMillis(accessTokenExpiration))
                .sameSite("Strict")
                .domain(cookieDomain.isEmpty() ? null : cookieDomain)
                .build();
    }

    /**
     * Creates cookie that clears the access token
     */
    public ResponseCookie createAccessTokenClearCookie() {
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .domain(cookieDomain.isEmpty() ? null : cookieDomain)
                .build();
    }

    /**
     * Creates cookie that clears the refresh token
     */
    public ResponseCookie createRefreshTokenClearCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/api/auth/refresh-token")
                .maxAge(0)
                .sameSite("Strict")
                .domain(cookieDomain.isEmpty() ? null : cookieDomain)
                .build();
    }

    /**
     * Creates cookie that clears the CSRF token
     */
    public ResponseCookie createCsrfTokenClearCookie() {
        return ResponseCookie.from(CSRF_TOKEN_COOKIE, "")
                .httpOnly(false)
                .secure(secureCookie)
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .domain(cookieDomain.isEmpty() ? null : cookieDomain)
                .build();
    }

    /**
     * Extract access token from request cookies
     */
    public Optional<String> extractAccessToken(HttpServletRequest request) {
        return extractCookieValue(request, ACCESS_TOKEN_COOKIE);
    }

    /**
     * Extract refresh token from request cookies
     */
    public Optional<String> extractRefreshToken(HttpServletRequest request) {
        return extractCookieValue(request, REFRESH_TOKEN_COOKIE);
    }

    /**
     * Extract CSRF token from request cookies
     */
    public Optional<String> extractCsrfToken(HttpServletRequest request) {
        return extractCookieValue(request, CSRF_TOKEN_COOKIE);
    }

    private Optional<String> extractCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }

        return Arrays.stream(cookies)
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isEmpty())
                .findFirst();
    }

    /**
     * Add cookies to response
     */
    public void addCookiesToResponse(HttpServletResponse response,
                                     String accessToken,
                                     String refreshToken,
                                     String csrfToken) {
        response.addHeader("Set-Cookie", createAccessTokenCookie(accessToken).toString());
        response.addHeader("Set-Cookie", createRefreshTokenCookie(refreshToken).toString());
        response.addHeader("Set-Cookie", createCsrfTokenCookie(csrfToken).toString());
    }

    /**
     * Clear all auth cookies
     */
    public void clearAuthCookies(HttpServletResponse response) {
        response.addHeader("Set-Cookie", createAccessTokenClearCookie().toString());
        response.addHeader("Set-Cookie", createRefreshTokenClearCookie().toString());
        response.addHeader("Set-Cookie", createCsrfTokenClearCookie().toString());
    }
}
