package org.ozonLabel.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "invitations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_owner_id", nullable = false)
    private Long companyOwnerId;

    @Column(name = "invitee_email")
    private String inviteeEmail;

    @Column(name = "invitee_phone")
    private String inviteePhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private CompanyMember.MemberRole role = CompanyMember.MemberRole.VIEWER;

    @Column(name = "token", nullable = false, unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InvitationStatus status = InvitationStatus.PENDING;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "accepted_by_user_id")
    private Long acceptedByUserId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_owner_id", insertable = false, updatable = false)
    private User companyOwner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_by_user_id", insertable = false, updatable = false)
    private User acceptedByUser;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public enum InvitationStatus {
        PENDING,    // Ожидает принятия
        ACCEPTED,   // Принято
        EXPIRED,    // Истекло
        CANCELLED   // Отменено
    }
}