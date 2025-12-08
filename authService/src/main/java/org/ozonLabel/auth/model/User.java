package org.ozonLabel.auth.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_email", columnList = "email"),
        @Index(name = "idx_refresh_token", columnList = "refresh_token")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "company_name")
    private String companyName;

    @Column(length = 12)
    private String inn;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription", nullable = false)
    @Builder.Default
    private SubscriptionType subscription = SubscriptionType.FREE;

    @Column(name = "ozon_client_id")
    private String ozonClientId;

    @Column(name = "ozon_api_key")
    private String ozonApiKey;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "refresh_token", length = 512)
    private String refreshToken;

    @Column(name = "refresh_token_expires_at")
    private LocalDateTime refreshTokenExpiresAt;

    // ⭐ NEW: Security features
    @Column(name = "login_attempts")
    @Builder.Default
    private Integer loginAttempts = 0;

    @Column(name = "account_locked_until")
    private LocalDateTime accountLockedUntil;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @Column(name = "email_verified")
    @Builder.Default
    private Boolean emailVerified = true; // Auto-verified after code confirmation

    public enum SubscriptionType {
        FREE, BASIC, PRO, MAX
    }

    /**
     * Check if account is currently locked
     */
    public boolean isAccountLocked() {
        return accountLockedUntil != null && accountLockedUntil.isAfter(LocalDateTime.now());
    }

    /**
     * Check if refresh token is valid
     */
    public boolean isRefreshTokenValid(String token) {
        return refreshToken != null &&
                refreshToken.equals(token) &&
                refreshTokenExpiresAt != null &&
                refreshTokenExpiresAt.isAfter(LocalDateTime.now());
    }
}