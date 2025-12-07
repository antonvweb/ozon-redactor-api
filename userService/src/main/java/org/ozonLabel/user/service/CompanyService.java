package org.ozonLabel.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.exception.BadRequestException;
import org.ozonLabel.common.exception.BusinessException;
import org.ozonLabel.user.dto.*;
import org.ozonLabel.domain.model.CompanyAuditLog;
import org.ozonLabel.domain.model.CompanyMember;
import org.ozonLabel.domain.model.Invitation;
import org.ozonLabel.domain.repository.CompanyMemberRepository;
import org.ozonLabel.domain.repository.InvitationRepository;
import org.ozonLabel.domain.model.User;
import org.ozonLabel.domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyService {

    private final CompanyMemberRepository companyMemberRepository;
    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.invitation.expiration.hours:72}")
    private int invitationExpirationHours;

    /**
     * Отправить приглашение пользователю
     */
    public InviteUserResponseDto inviteUser(String ownerEmail, InviteUserRequestDto request) {
        if (request.isEmpty()) {
            throw new BadRequestException("Необходимо указать email или телефон");
        }

        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Владелец не найден"));

        if (request.getEmail() != null && request.getEmail().equals(ownerEmail)) {
            throw new BadRequestException("Вы не можете пригласить самого себя");
        }

        if (request.getEmail() != null) {
            boolean hasActive = invitationRepository.existsByCompanyOwnerIdAndInviteeEmailAndStatus(
                    owner.getId(), request.getEmail(), Invitation.InvitationStatus.PENDING);
            if (hasActive) {
                throw new BadRequestException("Приглашение для этого email уже отправлено");
            }

            // ←←←←←←←←←←←←←←←←← НОВАЯ ПРОВЕРКА ЗДЕСЬ ←←←←←←←←←←←←←←←←←
            User targetUser = userRepository.findByEmailCaseInsensitive(request.getEmail().trim()).orElse(null);
            if (targetUser != null) {
                boolean alreadyMember = companyMemberRepository.existsByCompanyOwnerIdAndMemberUserId(
                        owner.getId(), targetUser.getId());
                if (alreadyMember) {
                    throw new BadRequestException("Этот пользователь уже состоит в вашей компании");
                }
            }
            // ←←←←←←←←←←←←←←←←← КОНЕЦ НОВОЙ ПРОВЕРКИ ←←←←←←←←←←←←←←←←←
        }

        if (request.getPhone() != null) {
            boolean hasActive = invitationRepository.existsByCompanyOwnerIdAndInviteePhoneAndStatus(
                    owner.getId(), request.getPhone(), Invitation.InvitationStatus.PENDING);
            if (hasActive) {
                throw new BadRequestException("Приглашение для этого телефона уже отправлено");
            }

            // ←←←←←←←←←←←←←← ПРАВИЛЬНАЯ ПРОВЕРКА ПО ТЕЛЕФОНУ ←←←←←←←←←←←←←
            User targetUser = userRepository.findByPhone(request.getPhone().trim()).orElse(null);
            if (targetUser != null) {
                boolean alreadyMember = companyMemberRepository.existsByCompanyOwnerIdAndMemberUserId(
                        owner.getId(), targetUser.getId());
                if (alreadyMember) {
                    throw new BadRequestException("Этот пользователь уже состоит в вашей компании");
                }
            }
            // ←←←←←←←←←←←←← КОНЕЦ ПРАВИЛЬНОЙ ПРОВЕРКИ ←←←←←←←←←←←←←
        }

        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(invitationExpirationHours);

        Invitation invitation = new Invitation();
        invitation.setCompanyOwnerId(owner.getId());
        invitation.setInviteeEmail(request.getEmail());
        invitation.setInviteePhone(request.getPhone());
        invitation.setRole(request.getRole());
        invitation.setToken(token);
        invitation.setStatus(Invitation.InvitationStatus.PENDING);
        invitation.setExpiresAt(expiresAt);

        invitation = invitationRepository.save(invitation);

        String invitationLink = frontendUrl + "/accept-invitation/" + token;

        // Создаем уведомление вместо email
        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            User invitedUser = userRepository.findByEmailCaseInsensitive(request.getEmail().trim()).orElse(null);
            if (invitedUser != null) {
                // Пользователь уже зарегистрирован - создаем уведомление
                notificationService.createInvitationNotification(
                        invitedUser.getId(),
                        invitation.getId(),
                        owner.getId(),
                        owner.getName(),
                        owner.getCompanyName() != null ? owner.getCompanyName() : "компанию",
                        getRoleInRussian(request.getRole())
                );
            } else {
                // Пользователь не зарегистрирован - отправляем email
                sendInvitationEmail(owner, request.getEmail(), invitationLink, request.getRole());
            }
        }

        // Логируем действие
        Map<String, Object> details = new HashMap<>();
        details.put("inviteeEmail", request.getEmail());
        details.put("inviteePhone", request.getPhone());
        details.put("role", request.getRole().name());
        auditLogService.logAction(owner.getId(), owner.getId(),
                CompanyAuditLog.AuditAction.INVITE_SENT, "INVITATION", invitation.getId(), details);

        log.info("Приглашение отправлено от {} для {} с ролью {}",
                ownerEmail,
                request.getEmail() != null ? request.getEmail() : request.getPhone(),
                request.getRole());

        return InviteUserResponseDto.builder()
                .invitationId(invitation.getId())
                .token(token)
                .invitationLink(invitationLink)
                .inviteeEmail(request.getEmail())
                .inviteePhone(request.getPhone())
                .role(request.getRole().name())
                .expiresAt(expiresAt)
                .message("Приглашение успешно отправлено")
                .build();
    }

    @Transactional
    public void acceptInvitationById(Long invitationId, String acceptingUserEmail) {
        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Приглашение не найдено"));

        if (invitation.getStatus() != Invitation.InvitationStatus.PENDING) {
            throw new BadRequestException("Приглашение уже использовано или отменено");
        }

        if (invitation.isExpired()) {
            invitation.setStatus(Invitation.InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new BadRequestException("Срок действия приглашения истек");
        }

        User acceptingUser = userRepository.findByEmail(acceptingUserEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));

        // Проверка email/phone
        boolean matches = false;
        if (invitation.getInviteeEmail() != null &&
                invitation.getInviteeEmail().equalsIgnoreCase(acceptingUser.getEmail())) {
            matches = true;
        }
        if (invitation.getInviteePhone() != null &&
                invitation.getInviteePhone().equals(acceptingUser.getPhone())) {
            matches = true;
        }
        if (!matches) {
            throw new BadRequestException("Это приглашение предназначено другому пользователю");
        }

        // Проверка, что ещё не в компании
        if (companyMemberRepository.existsByCompanyOwnerIdAndMemberUserId(
                invitation.getCompanyOwnerId(), acceptingUser.getId())) {
            throw new BadRequestException("Вы уже являетесь членом этой компании");
        }

        // Присоединяем
        CompanyMember member = new CompanyMember();
        member.setCompanyOwnerId(invitation.getCompanyOwnerId());
        member.setMemberUserId(acceptingUser.getId());
        member.setRole(invitation.getRole());
        companyMemberRepository.save(member);

        invitation.setStatus(Invitation.InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(LocalDateTime.now());
        invitation.setAcceptedByUserId(acceptingUser.getId());
        invitationRepository.save(invitation);

        // Уведомляем владельца
        User owner = userRepository.findById(invitation.getCompanyOwnerId()).orElse(null);
        if (owner != null) {
            notificationService.createInvitationAcceptedNotification(
                    owner.getId(),
                    acceptingUser.getName(),
                    acceptingUser.getId(),
                    owner.getCompanyName() != null ? owner.getCompanyName() : "вашу компанию"
            );
        }

        log.info("Пользователь {} принял приглашение по ID {}", acceptingUserEmail, invitationId);
    }

    @Transactional
    public void rejectInvitationById(Long invitationId, String rejectingUserEmail) {
        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Приглашение не найдено"));

        if (invitation.getStatus() != Invitation.InvitationStatus.PENDING) {
            throw new BadRequestException("Приглашение уже обработано");
        }

        User user = userRepository.findByEmail(rejectingUserEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));

        boolean matches = false;
        if (invitation.getInviteeEmail() != null &&
                invitation.getInviteeEmail().equalsIgnoreCase(user.getEmail())) {
            matches = true;
        }
        if (invitation.getInviteePhone() != null &&
                invitation.getInviteePhone().equals(user.getPhone())) {
            matches = true;
        }
        if (!matches) {
            throw new BadRequestException("Это приглашение предназначено другому пользователю");
        }

        invitation.setStatus(Invitation.InvitationStatus.CANCELLED);
        invitationRepository.save(invitation);

        User owner = userRepository.findById(invitation.getCompanyOwnerId()).orElse(null);
        if (owner != null) {
            notificationService.createInvitationRejectedNotification(
                    owner.getId(),
                    user.getName(),
                    owner.getCompanyName() != null ? owner.getCompanyName() : "вашу компанию"
            );
        }
    }

    /**
     * Отменить приглашение
     */
    /**
     * Отменить приглашение
     */
    @Transactional
    public void cancelInvitation(String ownerEmail, Long invitationId) {
        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Владелец не найден"));

        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Приглашение не найдено"));

        if (!invitation.getCompanyOwnerId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Это не ваше приглашение");
        }

        if (invitation.getStatus() != Invitation.InvitationStatus.PENDING) {
            throw new BadRequestException("Можно отменить только активные приглашения");
        }

        invitation.setStatus(Invitation.InvitationStatus.CANCELLED);
        invitationRepository.save(invitation);

        // Логируем действие
        Map<String, Object> details = new HashMap<>();
        details.put("inviteeEmail", invitation.getInviteeEmail());
        details.put("inviteePhone", invitation.getInviteePhone());
        auditLogService.logAction(owner.getId(), owner.getId(),
                CompanyAuditLog.AuditAction.INVITATION_CANCELLED, "INVITATION", invitationId, details);

        log.info("Приглашение {} отменено владельцем {}", invitationId, ownerEmail);
    }

    /**
     * Принять приглашение
     */
    @Transactional
    public void acceptInvitation(String token, String acceptingUserEmail) {
        Invitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Приглашение не найдено"));

        if (invitation.getStatus() != Invitation.InvitationStatus.PENDING) {
            throw new BadRequestException("Приглашение уже использовано или отменено");
        }

        if (invitation.isExpired()) {
            invitation.setStatus(Invitation.InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new BadRequestException("Срок действия приглашения истек");
        }

        User acceptingUser = userRepository.findByEmail(acceptingUserEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));

        boolean contactMatches = false;
        if (invitation.getInviteeEmail() != null &&
                invitation.getInviteeEmail().equalsIgnoreCase(acceptingUser.getEmail())) {
            contactMatches = true;
        }
        if (invitation.getInviteePhone() != null &&
                invitation.getInviteePhone().equals(acceptingUser.getPhone())) {
            contactMatches = true;
        }

        if (!contactMatches) {
            throw new BadRequestException("Приглашение предназначено для другого пользователя");
        }

        boolean alreadyMember = companyMemberRepository.existsByCompanyOwnerIdAndMemberUserId(
                invitation.getCompanyOwnerId(), acceptingUser.getId());

        if (alreadyMember) {
            throw new BadRequestException("Вы уже являетесь членом этой компании");
        }

        CompanyMember member = new CompanyMember();
        member.setCompanyOwnerId(invitation.getCompanyOwnerId());
        member.setMemberUserId(acceptingUser.getId());
        member.setRole(invitation.getRole());

        companyMemberRepository.save(member);

        invitation.setStatus(Invitation.InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(LocalDateTime.now());
        invitation.setAcceptedByUserId(acceptingUser.getId());
        invitationRepository.save(invitation);

        // Создаем уведомление для владельца компании о принятии приглашения
        User owner = userRepository.findById(invitation.getCompanyOwnerId()).orElse(null);
        if (owner != null) {
            notificationService.createInvitationAcceptedNotification(
                    owner.getId(),
                    acceptingUser.getName(),
                    acceptingUser.getId(),
                    owner.getCompanyName() != null ? owner.getCompanyName() : "вашу компанию"
            );
        }

        // Логируем действие
        Map<String, Object> details = new HashMap<>();
        details.put("memberEmail", acceptingUser.getEmail());
        details.put("memberName", acceptingUser.getName());
        details.put("role", invitation.getRole().name());
        auditLogService.logAction(invitation.getCompanyOwnerId(), acceptingUser.getId(),
                CompanyAuditLog.AuditAction.MEMBER_JOINED, "MEMBER", acceptingUser.getId(), details);

        log.info("Пользователь {} принял приглашение в компанию владельца {}",
                acceptingUserEmail, invitation.getCompanyOwnerId());
    }

    /**
     * Отклонить приглашение
     */
    @Transactional
    public void rejectInvitation(String token, String rejectingUserEmail) {
        Invitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Приглашение не найдено"));

        if (invitation.getStatus() != Invitation.InvitationStatus.PENDING) {
            throw new BadRequestException("Приглашение уже использовано или отменено");
        }

        if (invitation.isExpired()) {
            invitation.setStatus(Invitation.InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new BadRequestException("Срок действия приглашения истек");
        }

        User rejectingUser = userRepository.findByEmail(rejectingUserEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));

        // Обновляем статус приглашения
        invitation.setStatus(Invitation.InvitationStatus.CANCELLED);
        invitationRepository.save(invitation);

        // Создаем уведомление для владельца компании об отклонении
        User owner = userRepository.findById(invitation.getCompanyOwnerId()).orElse(null);
        if (owner != null) {
            notificationService.createInvitationRejectedNotification(
                    owner.getId(),
                    rejectingUser.getName(),
                    owner.getCompanyName() != null ? owner.getCompanyName() : "вашу компанию"
            );
        }

        log.info("Пользователь {} отклонил приглашение в компанию владельца {}",
                rejectingUserEmail, invitation.getCompanyOwnerId());
    }

    /**
     * Изменить роль члена команды
     */
    @Transactional
    public void updateMemberRole(String adminEmail, Long companyOwnerId, Long memberId,
                                 CompanyMember.MemberRole newRole) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));

        // Проверяем, что админ имеет права
        if (!hasMinimumRole(adminEmail, companyOwnerId, CompanyMember.MemberRole.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недостаточно прав");
        }

        CompanyMember member = companyMemberRepository
                .findByCompanyOwnerIdAndMemberUserId(companyOwnerId, memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Член команды не найден"));

        CompanyMember.MemberRole oldRole = member.getRole();
        member.setRole(newRole);
        companyMemberRepository.save(member);

        // Логируем действие
        Map<String, Object> details = new HashMap<>();
        details.put("memberId", memberId);
        details.put("oldRole", oldRole.name());
        details.put("newRole", newRole.name());
        User memberUser = userRepository.findById(memberId).orElse(null);
        if (memberUser != null) {
            details.put("memberEmail", memberUser.getEmail());
            details.put("memberName", memberUser.getName());
        }
        auditLogService.logAction(companyOwnerId, admin.getId(),
                CompanyAuditLog.AuditAction.ROLE_CHANGED, "MEMBER", memberId, details);

        log.info("Роль пользователя {} изменена с {} на {} в компании {}",
                memberId, oldRole, newRole, companyOwnerId);
    }

    /**
     * Удалить члена команды
     */
    @Transactional
    public void removeMember(String adminEmail, Long companyOwnerId, Long memberId) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));

        // Проверяем, что админ имеет права
        if (!hasMinimumRole(adminEmail, companyOwnerId, CompanyMember.MemberRole.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недостаточно прав");
        }

        CompanyMember member = companyMemberRepository
                .findByCompanyOwnerIdAndMemberUserId(companyOwnerId, memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Член команды не найден"));

        // Логируем действие перед удалением
        Map<String, Object> details = new HashMap<>();
        details.put("memberId", memberId);
        details.put("role", member.getRole().name());
        User memberUser = userRepository.findById(memberId).orElse(null);
        if (memberUser != null) {
            details.put("memberEmail", memberUser.getEmail());
            details.put("memberName", memberUser.getName());
        }
        auditLogService.logAction(companyOwnerId, admin.getId(),
                CompanyAuditLog.AuditAction.MEMBER_REMOVED, "MEMBER", memberId, details);

        companyMemberRepository.deleteByCompanyOwnerIdAndMemberUserId(companyOwnerId, memberId);

        log.info("Пользователь {} удален из компании {} администратором {}",
                memberId, companyOwnerId, adminEmail);
    }

    /**
     * Получить список всех компаний пользователя
     */
    public UserCompaniesResponseDto getUserCompanies(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));

        List<CompanyMember> memberships = companyMemberRepository.findByMemberUserId(user.getId());

        List<UserCompaniesResponseDto.CompanyInfo> companies = memberships.stream()
                .map(membership -> {
                    User owner = userRepository.findById(membership.getCompanyOwnerId())
                            .orElse(null);

                    if (owner == null) {
                        return null;
                    }

                    return UserCompaniesResponseDto.CompanyInfo.builder()
                            .companyOwnerId(owner.getId())
                            .companyName(owner.getCompanyName())
                            .companyEmail(owner.getEmail())
                            .companyInn(owner.getInn())
                            .companyPhone(owner.getPhone())
                            .myRole(membership.getRole().name())
                            .subscription(owner.getSubscription())
                            .build();
                })
                .filter(info -> info != null)
                .collect(Collectors.toList());

        return UserCompaniesResponseDto.builder()
                .companies(companies)
                .build();
    }

    /**
     * Получить информацию о конкретной компании
     */
    public CompanyInfoResponseDto getCompanyInfo(String userEmail, Long companyOwnerId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));

        CompanyMember membership = companyMemberRepository
                .findByCompanyOwnerIdAndMemberUserId(companyOwnerId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "У вас нет доступа к этой компании"));

        User owner = userRepository.findById(companyOwnerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Компания не найдена"));

        CompanyInfoResponseDto.CompanyInfoResponseDtoBuilder builder = CompanyInfoResponseDto.builder()
                .ownerId(owner.getId())
                .ownerName(owner.getName())
                .ownerEmail(owner.getEmail())
                .companyName(owner.getCompanyName())
                .inn(owner.getInn())
                .phone(owner.getPhone())
                .ozonClientId(owner.getOzonClientId())
                .subscription(owner.getSubscription())
                .myRole(membership.getRole().name());

        if (membership.getRole() == CompanyMember.MemberRole.ADMIN) {
            List<CompanyMember> allMembers = companyMemberRepository.findByCompanyOwnerId(companyOwnerId);

            List<CompanyInfoResponseDto.TeamMember> teamMembers = allMembers.stream()
                    .map(member -> {
                        User memberUser = userRepository.findById(member.getMemberUserId())
                                .orElse(null);

                        if (memberUser == null) {
                            return null;
                        }

                        return CompanyInfoResponseDto.TeamMember.builder()
                                .userId(memberUser.getId())
                                .userName(memberUser.getName())
                                .userEmail(memberUser.getEmail())
                                .role(member.getRole().name())
                                .build();
                    })
                    .filter(tm -> tm != null)
                    .collect(Collectors.toList());

            builder.teamMembers(teamMembers);
        }

        return builder.build();
    }

    /**
     * Проверить права доступа пользователя в компании
     */
    public CompanyMember.MemberRole checkAccess(String userEmail, Long companyOwnerId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));

        return companyMemberRepository.findRoleByCompanyOwnerIdAndMemberUserId(companyOwnerId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "У вас нет доступа к этой компании"));
    }

    /**
     * Проверить, имеет ли пользователь минимальную требуемую роль
     */
    public boolean hasMinimumRole(String userEmail, Long companyOwnerId, CompanyMember.MemberRole minimumRole) {
        try {
            CompanyMember.MemberRole userRole = checkAccess(userEmail, companyOwnerId);

            int userRoleLevel = getRoleLevel(userRole);
            int requiredRoleLevel = getRoleLevel(minimumRole);

            return userRoleLevel >= requiredRoleLevel;
        } catch (ResponseStatusException e) {
            return false;
        }
    }

    private int getRoleLevel(CompanyMember.MemberRole role) {
        switch (role) {
            case ADMIN: return 3;
            case MODERATOR: return 2;
            case VIEWER: return 1;
            default: return 0;
        }
    }

    private void sendInvitationEmail(User owner, String recipientEmail, String invitationLink,
                                     CompanyMember.MemberRole role) {
        try {
            String roleRu = switch (role) {
                case ADMIN -> "Администратор";
                case MODERATOR -> "Модератор";
                case VIEWER -> "Наблюдатель";
            };

            String subject = "Приглашение в команду — " + owner.getCompanyName();
            String text = String.format("""
                    Здравствуйте!
                    
                    %s (%s) приглашает вас присоединиться к команде компании "%s".
                    
                    Ваша роль: %s
                    
                    Для принятия приглашения перейдите по ссылке:
                    %s
                    
                    Приглашение действительно в течение %d часов.
                    
                    Если вы не ожидали это приглашение, просто проигнорируйте это письмо.
                    
                    --
                    С уважением,
                    Команда ozonLabel
                    """,
                    owner.getName(),
                    owner.getEmail(),
                    owner.getCompanyName() != null ? owner.getCompanyName() : "компании",
                    roleRu,
                    invitationLink,
                    invitationExpirationHours
            );

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("a.volkov@print-365.ru");
            message.setTo(recipientEmail);
            message.setSubject(subject);
            message.setText(text);

            mailSender.send(message);

            log.info("Письмо с приглашением отправлено на {}", recipientEmail);
        } catch (Exception e) {
            log.error("Ошибка при отправке письма с приглашением", e);
        }
    }

    private String getRoleInRussian(CompanyMember.MemberRole role) {
        return switch (role) {
            case ADMIN -> "Администратор";
            case MODERATOR -> "Модератор";
            case VIEWER -> "Наблюдатель";
        };
    }
}