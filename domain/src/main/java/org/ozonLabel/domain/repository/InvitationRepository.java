package org.ozonLabel.domain.repository;

import org.ozonLabel.domain.model.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    // Найти приглашение по токену
    Optional<Invitation> findByToken(String token);

    // Найти все приглашения от владельца компании
    List<Invitation> findByCompanyOwnerId(Long companyOwnerId);

    // Найти активные (pending) приглашения от владельца
    List<Invitation> findByCompanyOwnerIdAndStatus(Long companyOwnerId, Invitation.InvitationStatus status);

    // Найти приглашения по email или phone
    @Query("SELECT i FROM Invitation i WHERE " +
            "(i.inviteeEmail = :email OR i.inviteePhone = :phone) AND " +
            "i.companyOwnerId = :ownerId AND i.status = :status")
    List<Invitation> findPendingInvitationsByContact(
            @Param("email") String email,
            @Param("phone") String phone,
            @Param("ownerId") Long ownerId,
            @Param("status") Invitation.InvitationStatus status
    );

    // Найти истекшие приглашения
    @Query("SELECT i FROM Invitation i WHERE i.status = 'PENDING' AND i.expiresAt < :now")
    List<Invitation> findExpiredInvitations(@Param("now") LocalDateTime now);

    // Проверить, есть ли активное приглашение
    boolean existsByCompanyOwnerIdAndInviteeEmailAndStatus(
            Long companyOwnerId,
            String inviteeEmail,
            Invitation.InvitationStatus status
    );

    boolean existsByCompanyOwnerIdAndInviteePhoneAndStatus(
            Long companyOwnerId,
            String inviteePhone,
            Invitation.InvitationStatus status
    );
}
