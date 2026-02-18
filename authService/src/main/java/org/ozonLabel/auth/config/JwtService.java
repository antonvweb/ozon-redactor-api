package org.ozonLabel.auth.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT Service for secure token generation and validation
 *
 * SECURITY FEATURES:
 * - Uses HS256 algorithm with strong secret key
 * - Separate access and refresh token expiration
 * - Token type claim to prevent token confusion attacks
 * - Proper exception handling
 */
@Service
@Slf4j
public class JwtService {

    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration:86400000}") // 24 hours default
    private long accessTokenExpiration;

    @Value("${jwt.refresh-expiration:1209600000}") // 14 days default
    private long refreshTokenExpiration;

    /**
     * Get the signing key from secret
     * SECURITY: Key is derived from secret using UTF-8 encoding
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generate access token for user
     */
    public String generateToken(String email) {
        return Jwts.builder()
                .subject(email)
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Generate refresh token for user
     * Uses longer expiration and different token type
     */
    public String generateRefreshToken(String email) {
        return Jwts.builder()
                .subject(email)
                .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Extract email (subject) from token
     */
    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Validate token and check it's not expired
     */
    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return !isTokenExpired(claims);
        } catch (ExpiredJwtException e) {
            log.debug("Token expired: {}", e.getMessage());
            return false;
        } catch (JwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Error validating token", e);
            return false;
        }
    }

    /**
     * Validate that token is a refresh token
     */
    public boolean isValidRefreshToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
            return REFRESH_TOKEN_TYPE.equals(tokenType) && !isTokenExpired(claims);
        } catch (Exception e) {
            log.warn("Invalid refresh token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract all claims from token
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Check if token is expired
     */
    private boolean isTokenExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }

    /**
     * Get access token expiration in milliseconds
     */
    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    /**
     * Get refresh token expiration in milliseconds
     */
    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }
}
