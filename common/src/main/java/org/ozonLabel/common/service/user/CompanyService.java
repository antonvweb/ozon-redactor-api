package org.ozonLabel.common.service.user;

import org.ozonLabel.common.dto.user.*;
import org.ozonLabel.common.model.InvitationStatus;
import org.ozonLabel.common.model.MemberRole;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CompanyService {

    /**
     * Приглашает пользователя в компанию
     */
    InviteUserResponseDto inviteUser(String ownerEmail, InviteUserRequestDto request);

    /**
     * Принятие приглашения (по токену)
     */
    void acceptInvitation(String token, String acceptingUserEmail);

    /**
     * Принятие приглашения по ID
     */
    void acceptInvitationById(Long invitationId, String acceptingUserEmail);

    /**
     * Отклонение приглашения (по токену)
     */
    void rejectInvitation(String token, String rejectingUserEmail);

    /**
     * Отклонение приглашения по ID
     */
    void rejectInvitationById(Long invitationId, String rejectingUserEmail);

    /**
     * Отмена приглашения владельцем
     */
    void cancelInvitation(String ownerEmail, Long invitationId);

    /**
     * Изменение роли участника компании
     */
    void updateMemberRole(String adminEmail, Long companyOwnerId, Long memberId, MemberRole newRole);

    /**
     * Удаление участника компании
     */
    void removeMember(String adminEmail, Long companyOwnerId, Long memberId);

    /**
     * Получение всех компаний, в которых состоит пользователь
     */
    UserCompaniesResponseDto getUserCompanies(String userEmail);

    /**
     * Получение информации о конкретной компании
     */
    CompanyInfoResponseDto getCompanyInfo(String userEmail, Long companyOwnerId);

    /**
     * Проверка роли пользователя в компании
     */
    MemberRole checkAccess(String userEmail, Long companyOwnerId);

    /**
     * Проверка, что пользователь имеет минимальную роль
     */
    boolean hasMinimumRole(String userEmail, Long companyOwnerId, MemberRole minimumRole);

    // Найти всех участников компании
    List<CompanyMemberDto> findByCompanyOwnerId(Long companyOwnerId);

    // Найти все связи пользователя с компаниями
    List<CompanyMemberDto> findByMemberUserId(Long memberUserId);

    // Найти конкретную связь участника с компанией
    Optional<CompanyMemberDto> findByCompanyOwnerIdAndMemberUserId(Long companyOwnerId, Long memberUserId);

    // Получить роль участника в компании
    Optional<MemberRole> findRoleByCompanyOwnerIdAndMemberUserId(Long companyOwnerId, Long memberId);

    // Проверка членства пользователя в компании
    boolean existsByCompanyOwnerIdAndMemberUserId(Long companyOwnerId, Long memberUserId);

    // Удаление участника из компании
    void deleteByCompanyOwnerIdAndMemberUserId(Long companyOwnerId, Long memberUserId);

    // Подсчёт количества участников в компании
    Long countByCompanyOwnerId(Long companyOwnerId);

    // Найти приглашение по токену
    Optional<InvitationDto> findByToken(String token);

    // Найти активные (pending) приглашения от владельца
    List<InvitationDto> findByCompanyOwnerIdAndStatus(Long companyOwnerId, InvitationStatus status);

    // Найти приглашения по email или phone
    List<InvitationDto> findPendingInvitationsByContact(String email, String phone, Long ownerId, InvitationStatus status);

    // Найти истекшие приглашения
    List<InvitationDto> findExpiredInvitations(LocalDateTime now);

    // Проверить, есть ли активное приглашение по email
    boolean existsByCompanyOwnerIdAndInviteeEmailAndStatus(Long companyOwnerId, String inviteeEmail, InvitationStatus status);

    // Проверить, есть ли активное приглашение по телефону
    boolean existsByCompanyOwnerIdAndInviteePhoneAndStatus(Long companyOwnerId, String inviteePhone, InvitationStatus status);
}
