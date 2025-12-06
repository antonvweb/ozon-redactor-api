package org.ozonLabel.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "company_members")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_owner_id", nullable = false)
    private Long companyOwnerId;

    @Column(name = "member_user_id", nullable = false)
    private Long memberUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private MemberRole role = MemberRole.VIEWER;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_owner_id", insertable = false, updatable = false)
    private User companyOwner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_user_id", insertable = false, updatable = false)
    private User memberUser;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum MemberRole {
        ADMIN,      // Полный доступ: управление данными компании, товарами, членами команды
        MODERATOR,  // Работа только с товарами
        VIEWER      // Только просмотр
    }
}