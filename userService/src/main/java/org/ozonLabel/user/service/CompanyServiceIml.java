package org.ozonLabel.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.dto.user.*;
import org.ozonLabel.common.exception.user.*;
import org.ozonLabel.common.model.AuditAction;
import org.ozonLabel.common.model.InvitationStatus;
import org.ozonLabel.common.model.MemberRole;
import org.ozonLabel.common.service.user.AuditLogService;
import org.ozonLabel.common.service.user.CompanyService;
import org.ozonLabel.common.service.user.NotificationService;
import org.ozonLabel.user.entity.CompanyMember;
import org.ozonLabel.user.entity.Invitation;
import org.ozonLabel.user.repository.CompanyMemberRepository;
import org.ozonLabel.user.repository.InvitationRepository;
import org.ozonLabel.user.entity.User;
import org.ozonLabel.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyServiceIml implements CompanyService {

    private final CompanyMemberRepository companyMemberRepository;
    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    @Value("${app.frontend.url:https://dev.print-365.ru}")
    private String frontendUrl;

    @Value("${app.invitation.expiration.hours:72}")
    private int invitationExpirationHours;

    @Transactional
    public InviteUserResponseDto inviteUser(String ownerEmail, InviteUserRequestDto request) {
        if (request.isEmpty()) {
            throw new ValidationException("Необходимо указать адрес электронной почты или номер телефона.");
        }

        User owner = getUserByEmail(ownerEmail);

        if (request.getEmail() != null && request.getEmail().equals(ownerEmail)) {
            throw new ValidationException("Не могу пригласить себя");
        }

        if (request.getEmail() != null) {
            checkExistingInvitation(owner.getId(), request.getEmail(), null);
            checkExistingMembership(owner.getId(), request.getEmail(), null);
        }

        if (request.getPhone() != null) {
            checkExistingInvitation(owner.getId(), null, request.getPhone());
            checkExistingMembership(owner.getId(), null, request.getPhone());
        }

        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(invitationExpirationHours);

        Invitation invitation = new Invitation();
        invitation.setCompanyOwnerId(owner.getId());
        invitation.setInviteeEmail(request.getEmail());
        invitation.setInviteePhone(request.getPhone());
        invitation.setRole(request.getRole());
        invitation.setToken(token);
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setExpiresAt(expiresAt);

        invitation = invitationRepository.save(invitation);

        String invitationLink = frontendUrl + "/accept-invitation/" + token;

        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            User invitedUser = userRepository.findByEmailCaseInsensitive(request.getEmail().trim()).orElse(null);
            if (invitedUser != null) {
                notificationService.createInvitationNotification(
                        invitedUser.getId(),
                        invitation.getId(),
                        owner.getId(),
                        owner.getName(),
                        owner.getCompanyName() != null ? owner.getCompanyName() : "company",
                        getRoleInEnglish(request.getRole())
                );
            } else {
                sendInvitationEmail(owner, request.getEmail(), invitationLink, request.getRole());
            }
        }

        Map<String, Object> details = new HashMap<>();
        details.put("inviteeEmail", request.getEmail());
        details.put("inviteePhone", request.getPhone());
        details.put("role", request.getRole().name());
        auditLogService.logAction(owner.getId(), owner.getId(),
                AuditAction.INVITE_SENT, "INVITATION", invitation.getId(), details);

        log.info("Invitation sent from {} for {} with role {}",
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
                .message("Invitation sent successfully")
                .build();
    }

    /**
     * Unified method to accept invitation (by token or ID)
     */
    @Transactional
    public void acceptInvitation(String identifier, String acceptingUserEmail, boolean isToken) {
        Invitation invitation = isToken
                ? invitationRepository.findByToken(identifier)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation"))
                : invitationRepository.findById(Long.parseLong(identifier))
                .orElseThrow(() -> new ResourceNotFoundException("Invitation"));

        validateInvitationStatus(invitation);

        User acceptingUser = getUserByEmail(acceptingUserEmail);
        validateInvitationRecipient(invitation, acceptingUser);
        validateNotAlreadyMember(invitation.getCompanyOwnerId(), acceptingUser.getId());

        CompanyMember member = new CompanyMember();
        member.setCompanyOwnerId(invitation.getCompanyOwnerId());
        member.setMemberUserId(acceptingUser.getId());
        member.setRole(invitation.getRole());
        companyMemberRepository.save(member);

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(LocalDateTime.now());
        invitation.setAcceptedByUserId(acceptingUser.getId());
        invitationRepository.save(invitation);

        User owner = userRepository.findById(invitation.getCompanyOwnerId()).orElse(null);
        if (owner != null) {
            notificationService.createInvitationAcceptedNotification(
                    owner.getId(),
                    acceptingUser.getName(),
                    acceptingUser.getId(),
                    owner.getCompanyName() != null ? owner.getCompanyName() : "your company"
            );
        }

        Map<String, Object> details = new HashMap<>();
        details.put("memberEmail", acceptingUser.getEmail());
        details.put("memberName", acceptingUser.getName());
        details.put("role", invitation.getRole().name());
        auditLogService.logAction(invitation.getCompanyOwnerId(), acceptingUser.getId(),
                AuditAction.MEMBER_JOINED, "MEMBER", acceptingUser.getId(), details);

        log.info("User {} accepted invitation to company {}",
                acceptingUserEmail, invitation.getCompanyOwnerId());
    }

    @Transactional
    public void acceptInvitation(String token, String acceptingUserEmail) {
        acceptInvitation(token, acceptingUserEmail, true);
    }

    @Transactional
    public void acceptInvitationById(Long invitationId, String acceptingUserEmail) {
        acceptInvitation(invitationId.toString(), acceptingUserEmail, false);
    }

    /**
     * Unified method to reject invitation (by token or ID)
     */
    @Transactional
    public void rejectInvitation(String identifier, String rejectingUserEmail, boolean isToken) {
        Invitation invitation = isToken
                ? invitationRepository.findByToken(identifier)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation"))
                : invitationRepository.findById(Long.parseLong(identifier))
                .orElseThrow(() -> new ResourceNotFoundException("Invitation"));

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new InvalidInvitationException("Приглашение уже обработано");
        }

        User rejectingUser = getUserByEmail(rejectingUserEmail);
        validateInvitationRecipient(invitation, rejectingUser);

        invitation.setStatus(InvitationStatus.CANCELLED);
        invitationRepository.save(invitation);

        User owner = userRepository.findById(invitation.getCompanyOwnerId()).orElse(null);
        if (owner != null) {
            notificationService.createInvitationRejectedNotification(
                    owner.getId(),
                    rejectingUser.getName(),
                    owner.getCompanyName() != null ? owner.getCompanyName() : "your company"
            );
        }

        log.info("User {} rejected invitation to company {}",
                rejectingUserEmail, invitation.getCompanyOwnerId());
    }

    @Transactional
    public void rejectInvitation(String token, String rejectingUserEmail) {
        rejectInvitation(token, rejectingUserEmail, true);
    }

    @Transactional
    public void rejectInvitationById(Long invitationId, String rejectingUserEmail) {
        rejectInvitation(invitationId.toString(), rejectingUserEmail, false);
    }

    @Transactional
    public void cancelInvitation(String ownerEmail, Long invitationId) {
        User owner = getUserByEmail(ownerEmail);

        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Приглашение"));

        if (!invitation.getCompanyOwnerId().equals(owner.getId())) {
            throw new AccessDeniedException("Это не ваше приглашение");
        }

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new InvalidInvitationException("Можно отменить только ожидающие приглашения.");
        }

        invitation.setStatus(InvitationStatus.CANCELLED);
        invitationRepository.save(invitation);

        Map<String, Object> details = new HashMap<>();
        details.put("inviteeEmail", invitation.getInviteeEmail());
        details.put("inviteePhone", invitation.getInviteePhone());
        auditLogService.logAction(owner.getId(), owner.getId(),
                AuditAction.INVITATION_CANCELLED, "INVITATION", invitationId, details);

        log.info("Invitation {} cancelled by owner {}", invitationId, ownerEmail);
    }

    @Transactional
    public void updateMemberRole(String adminEmail, Long companyOwnerId, Long memberId,
                                 MemberRole newRole) {
        User admin = getUserByEmail(adminEmail);

        if (!hasMinimumRole(adminEmail, companyOwnerId, MemberRole.ADMIN)) {
            throw new AccessDeniedException("Недостаточные права доступа");
        }

        CompanyMember member = companyMemberRepository
                .findByCompanyOwnerIdAndMemberUserId(companyOwnerId, memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Team member"));

        MemberRole oldRole = member.getRole();
        member.setRole(newRole);
        companyMemberRepository.save(member);

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
                AuditAction.ROLE_CHANGED, "MEMBER", memberId, details);

        log.info("User {} role changed from {} to {} in company {}",
                memberId, oldRole, newRole, companyOwnerId);
    }

    @Transactional
    public void removeMember(String adminEmail, Long companyOwnerId, Long memberId) {
        User admin = getUserByEmail(adminEmail);

        if (!hasMinimumRole(adminEmail, companyOwnerId, MemberRole.ADMIN)) {
            throw new AccessDeniedException("Недостаточные права доступа");
        }

        CompanyMember member = companyMemberRepository
                .findByCompanyOwnerIdAndMemberUserId(companyOwnerId, memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Член команды"));

        Map<String, Object> details = new HashMap<>();
        details.put("memberId", memberId);
        details.put("role", member.getRole().name());
        User memberUser = userRepository.findById(memberId).orElse(null);
        if (memberUser != null) {
            details.put("memberEmail", memberUser.getEmail());
            details.put("memberName", memberUser.getName());
        }
        auditLogService.logAction(companyOwnerId, admin.getId(),
                AuditAction.MEMBER_REMOVED, "MEMBER", memberId, details);

        companyMemberRepository.deleteByCompanyOwnerIdAndMemberUserId(companyOwnerId, memberId);

        log.info("User {} removed from company {} by admin {}",
                memberId, companyOwnerId, adminEmail);
    }

    /**
     * Get user companies with N+1 fixed
     */
    public UserCompaniesResponseDto getUserCompanies(String userEmail) {
        User user = getUserByEmail(userEmail);
        List<CompanyMember> memberships = companyMemberRepository.findByMemberUserId(user.getId());

        // Fetch all owners in one query
        List<Long> ownerIds = memberships.stream()
                .map(CompanyMember::getCompanyOwnerId)
                .collect(Collectors.toList());

        Map<Long, User> ownerMap = userRepository.findAllById(ownerIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<UserCompaniesResponseDto.CompanyInfo> companies = memberships.stream()
                .map(membership -> {
                    User owner = ownerMap.get(membership.getCompanyOwnerId());
                    if (owner == null) return null;

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
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return UserCompaniesResponseDto.builder()
                .companies(companies)
                .build();
    }

    /**
     * Get company info with N+1 fixed
     */
    public CompanyInfoResponseDto getCompanyInfo(String userEmail, Long companyOwnerId) {
        User user = getUserByEmail(userEmail);

        CompanyMember membership = companyMemberRepository
                .findByCompanyOwnerIdAndMemberUserId(companyOwnerId, user.getId())
                .orElseThrow(() -> new AccessDeniedException("Нет доступа к этой компании"));

        User owner = userRepository.findById(companyOwnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Company"));

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

        if (membership.getRole() == MemberRole.ADMIN) {
            List<CompanyMember> allMembers = companyMemberRepository.findByCompanyOwnerId(companyOwnerId);

            // Fetch all member users in one query
            List<Long> memberIds = allMembers.stream()
                    .map(CompanyMember::getMemberUserId)
                    .collect(Collectors.toList());

            Map<Long, User> memberMap = userRepository.findAllById(memberIds).stream()
                    .collect(Collectors.toMap(User::getId, u -> u));

            List<CompanyInfoResponseDto.TeamMember> teamMembers = allMembers.stream()
                    .map(member -> {
                        User memberUser = memberMap.get(member.getMemberUserId());
                        if (memberUser == null) return null;

                        return CompanyInfoResponseDto.TeamMember.builder()
                                .userId(memberUser.getId())
                                .userName(memberUser.getName())
                                .userEmail(memberUser.getEmail())
                                .role(member.getRole().name())
                                .build();
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            builder.teamMembers(teamMembers);
        }

        return builder.build();
    }

    /**
     * Check access with caching for frequently called method
     */
    @Cacheable(value = "userCompanyAccess", key = "#userEmail + '_' + #companyOwnerId")
    public MemberRole checkAccess(String userEmail, Long companyOwnerId) {
        User user = getUserByEmail(userEmail);

        if (user.getId().equals(companyOwnerId)) {
            return MemberRole.ADMIN;
        }

        return companyMemberRepository.findRoleByCompanyOwnerIdAndMemberUserId(companyOwnerId, user.getId())
                .orElseThrow(() -> new AccessDeniedException("No access to this company"));
    }

    public boolean hasMinimumRole(String userEmail, Long companyOwnerId, MemberRole minimumRole) {
        try {
            MemberRole userRole = checkAccess(userEmail, companyOwnerId);
            return getRoleLevel(userRole) >= getRoleLevel(minimumRole);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<CompanyMemberDto> findByCompanyOwnerId(Long companyOwnerId) {
        List<CompanyMember> members = companyMemberRepository.findByCompanyOwnerId(companyOwnerId);
        List<Long> userIds = members.stream()
                .map(CompanyMember::getMemberUserId)
                .toList();
        Map<Long, User> usersMap = userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return members.stream()
                .map(member -> mapToDto(member, usersMap))
                .toList();
    }

    @Override
    public List<CompanyMemberDto> findByMemberUserId(Long memberUserId) {
        List<CompanyMember> memberships = companyMemberRepository.findByMemberUserId(memberUserId);
        List<Long> ownerIds = memberships.stream()
                .map(CompanyMember::getCompanyOwnerId)
                .toList();
        Map<Long, User> ownersMap = userRepository.findAllById(ownerIds)
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return memberships.stream()
                .map(member -> mapToDto(member, ownersMap))
                .toList();
    }

    @Override
    public Optional<CompanyMemberDto> findByCompanyOwnerIdAndMemberUserId(Long companyOwnerId, Long memberUserId) {
        return companyMemberRepository.findByCompanyOwnerIdAndMemberUserId(companyOwnerId, memberUserId)
                .map(member -> {
                    Map<Long, User> usersMap = Map.of(member.getCompanyOwnerId(), member.getCompanyOwner(),
                            member.getMemberUserId(), member.getMemberUser());
                    return mapToDto(member, usersMap);
                });
    }

    @Override
    public Optional<MemberRole> findRoleByCompanyOwnerIdAndMemberUserId(Long companyOwnerId, Long memberId) {
        return companyMemberRepository.findRoleByCompanyOwnerIdAndMemberUserId(companyOwnerId, memberId);
    }

    @Override
    public boolean existsByCompanyOwnerIdAndMemberUserId(Long companyOwnerId, Long memberUserId) {
        return companyMemberRepository.existsByCompanyOwnerIdAndMemberUserId(companyOwnerId, memberUserId);
    }

    @Override
    public void deleteByCompanyOwnerIdAndMemberUserId(Long companyOwnerId, Long memberUserId) {
        companyMemberRepository.deleteByCompanyOwnerIdAndMemberUserId(companyOwnerId, memberUserId);
    }

    @Override
    public Long countByCompanyOwnerId(Long companyOwnerId) {
        return companyMemberRepository.countByCompanyOwnerId(companyOwnerId);
    }

    @Override
    public Optional<InvitationDto> findByToken(String token) {
        return invitationRepository.findByToken(token)
                .map(this::mapToDto);
    }

    @Override
    public List<InvitationDto> findByCompanyOwnerIdAndStatus(Long companyOwnerId, InvitationStatus status) {
        return invitationRepository.findByCompanyOwnerIdAndStatus(companyOwnerId, status)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    public List<InvitationDto> findPendingInvitationsByContact(String email, String phone, Long ownerId, InvitationStatus status) {
        return invitationRepository.findPendingInvitationsByContact(email, phone, ownerId, status)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    public List<InvitationDto> findExpiredInvitations(LocalDateTime now) {
        return invitationRepository.findExpiredInvitations(now)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    public boolean existsByCompanyOwnerIdAndInviteeEmailAndStatus(Long companyOwnerId, String inviteeEmail, InvitationStatus status) {
        return invitationRepository.existsByCompanyOwnerIdAndInviteeEmailAndStatus(companyOwnerId, inviteeEmail, status);
    }

    @Override
    public boolean existsByCompanyOwnerIdAndInviteePhoneAndStatus(Long companyOwnerId, String inviteePhone, InvitationStatus status) {
        return invitationRepository.existsByCompanyOwnerIdAndInviteePhoneAndStatus(companyOwnerId, inviteePhone, status);
    }

    // Helper methods

    private void checkExistingInvitation(Long ownerId, String email, String phone) {
        boolean exists = email != null
                ? invitationRepository.existsByCompanyOwnerIdAndInviteeEmailAndStatus(
                ownerId, email, InvitationStatus.PENDING)
                : invitationRepository.existsByCompanyOwnerIdAndInviteePhoneAndStatus(
                ownerId, phone, InvitationStatus.PENDING);

        if (exists) {
            throw new DuplicateResourceException("Активное приглашение уже существует");
        }
    }

    private void checkExistingMembership(Long ownerId, String email, String phone) {
        User targetUser = email != null
                ? userRepository.findByEmailCaseInsensitive(email.trim()).orElse(null)
                : userRepository.findByPhone(phone.trim()).orElse(null);

        if (targetUser != null) {
            boolean alreadyMember = companyMemberRepository.existsByCompanyOwnerIdAndMemberUserId(
                    ownerId, targetUser.getId());
            if (alreadyMember) {
                throw new DuplicateResourceException("Пользователь уже является участником.");
            }
        }
    }

    private void validateInvitationStatus(Invitation invitation) {
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new InvalidInvitationException("Приглашение уже использовано или отменено");
        }

        if (invitation.isExpired()) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new InvalidInvitationException("Срок действия приглашения истек.");
        }
    }

    private void validateInvitationRecipient(Invitation invitation, User user) {
        boolean matches = (invitation.getInviteeEmail() != null &&
                invitation.getInviteeEmail().equalsIgnoreCase(user.getEmail())) ||
                (invitation.getInviteePhone() != null &&
                        invitation.getInviteePhone().equals(user.getPhone()));

        if (!matches) {
            throw new InvalidInvitationException("Приглашение предназначено для другого пользователя.");
        }
    }

    private void validateNotAlreadyMember(Long companyOwnerId, Long userId) {
        if (companyMemberRepository.existsByCompanyOwnerIdAndMemberUserId(companyOwnerId, userId)) {
            throw new DuplicateResourceException("Уже являюсь членом этой компании.");
        }
    }

    private int getRoleLevel(MemberRole role) {
        return switch (role) {
            case ADMIN -> 3;
            case MODERATOR -> 2;
            case VIEWER -> 1;
        };
    }

    private CompanyMemberDto mapToDto(CompanyMember member, Map<Long, User> usersMap) {
        User owner = usersMap.get(member.getCompanyOwnerId());
        User memberUser = usersMap.get(member.getMemberUserId());

        return CompanyMemberDto.builder()
                .id(member.getId())
                .companyOwnerId(member.getCompanyOwnerId())
                .memberUserId(member.getMemberUserId())
                .role(member.getRole())
                .createdAt(member.getCreatedAt())
                .updatedAt(member.getUpdatedAt())
                .companyOwnerName(owner != null ? owner.getName() : null)
                .companyOwnerEmail(owner != null ? owner.getEmail() : null)
                .memberUserName(memberUser != null ? memberUser.getName() : null)
                .memberUserEmail(memberUser != null ? memberUser.getEmail() : null)
                .build();
    }

    private InvitationDto mapToDto(Invitation invitation) {
        return InvitationDto.builder()
                .id(invitation.getId())
                .companyOwnerId(invitation.getCompanyOwnerId())
                .inviteeEmail(invitation.getInviteeEmail())
                .inviteePhone(invitation.getInviteePhone())
                .role(invitation.getRole())
                .token(invitation.getToken())
                .status(invitation.getStatus())
                .expiresAt(invitation.getExpiresAt())
                .acceptedAt(invitation.getAcceptedAt())
                .acceptedByUserId(invitation.getAcceptedByUserId())
                .createdAt(invitation.getCreatedAt())
                .updatedAt(invitation.getUpdatedAt())
                .build();
    }


    private void sendInvitationEmail(User owner, String recipientEmail, String invitationLink,
                                     MemberRole role) {
        try {
            String roleEn = getRoleInEnglish(role);
            String subject = "Приглашение команды — " + owner.getCompanyName();
            String text = String.format("""
                            Привет!
                            
                                          %s (%s) приглашает вас присоединиться к команде "%s".
                            
                                          Ваша роль: %s
                            
                                          Чтобы принять приглашение, перейдите по ссылке:
                                          %s
                            
                                          Приглашение действительно в течение %d часов.
                            
                                          Если вы не ожидали это приглашение, просто проигнорируйте это письмо.
                            
                                          --
                                          С уважением,
                                          Команда ozonLabel
                            
                    """,
                    owner.getName(),
                    owner.getEmail(),
                    owner.getCompanyName() != null ? owner.getCompanyName() : "the company",
                    roleEn,
                    invitationLink,
                    invitationExpirationHours
            );

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("a.volkov@print-365.ru");
            message.setTo(recipientEmail);
            message.setSubject(subject);
            message.setText(text);

            mailSender.send(message);
            log.info("Invitation email sent to {}", recipientEmail);
        } catch (Exception e) {
            log.error("Failed to send invitation email", e);
        }
    }

    private String getRoleInEnglish(MemberRole role) {
        return switch (role) {
            case ADMIN -> "Administrator";
            case MODERATOR -> "Moderator";
            case VIEWER -> "Viewer";
        };
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User"));
    }
}