package org.ozonLabel.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.exception.*;
import org.ozonLabel.user.dto.*;
import org.ozonLabel.domain.model.CompanyAuditLog;
import org.ozonLabel.domain.model.CompanyMember;
import org.ozonLabel.domain.model.Invitation;
import org.ozonLabel.domain.repository.CompanyMemberRepository;
import org.ozonLabel.domain.repository.InvitationRepository;
import org.ozonLabel.domain.model.User;
import org.ozonLabel.domain.repository.UserRepository;
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

    @Transactional
    public InviteUserResponseDto inviteUser(String ownerEmail, InviteUserRequestDto request) {
        if (request.isEmpty()) {
            throw new ValidationException("Email or phone must be provided");
        }

        User owner = getUserByEmail(ownerEmail);

        if (request.getEmail() != null && request.getEmail().equals(ownerEmail)) {
            throw new ValidationException("Cannot invite yourself");
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
        invitation.setStatus(Invitation.InvitationStatus.PENDING);
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
                CompanyAuditLog.AuditAction.INVITE_SENT, "INVITATION", invitation.getId(), details);

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

        invitation.setStatus(Invitation.InvitationStatus.ACCEPTED);
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
                CompanyAuditLog.AuditAction.MEMBER_JOINED, "MEMBER", acceptingUser.getId(), details);

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

        if (invitation.getStatus() != Invitation.InvitationStatus.PENDING) {
            throw new InvalidInvitationException("Invitation already processed");
        }

        User rejectingUser = getUserByEmail(rejectingUserEmail);
        validateInvitationRecipient(invitation, rejectingUser);

        invitation.setStatus(Invitation.InvitationStatus.CANCELLED);
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
                .orElseThrow(() -> new ResourceNotFoundException("Invitation"));

        if (!invitation.getCompanyOwnerId().equals(owner.getId())) {
            throw new AccessDeniedException("Not your invitation");
        }

        if (invitation.getStatus() != Invitation.InvitationStatus.PENDING) {
            throw new InvalidInvitationException("Can only cancel pending invitations");
        }

        invitation.setStatus(Invitation.InvitationStatus.CANCELLED);
        invitationRepository.save(invitation);

        Map<String, Object> details = new HashMap<>();
        details.put("inviteeEmail", invitation.getInviteeEmail());
        details.put("inviteePhone", invitation.getInviteePhone());
        auditLogService.logAction(owner.getId(), owner.getId(),
                CompanyAuditLog.AuditAction.INVITATION_CANCELLED, "INVITATION", invitationId, details);

        log.info("Invitation {} cancelled by owner {}", invitationId, ownerEmail);
    }

    @Transactional
    public void updateMemberRole(String adminEmail, Long companyOwnerId, Long memberId,
                                 CompanyMember.MemberRole newRole) {
        User admin = getUserByEmail(adminEmail);

        if (!hasMinimumRole(adminEmail, companyOwnerId, CompanyMember.MemberRole.ADMIN)) {
            throw new AccessDeniedException("Insufficient permissions");
        }

        CompanyMember member = companyMemberRepository
                .findByCompanyOwnerIdAndMemberUserId(companyOwnerId, memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Team member"));

        CompanyMember.MemberRole oldRole = member.getRole();
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
                CompanyAuditLog.AuditAction.ROLE_CHANGED, "MEMBER", memberId, details);

        log.info("User {} role changed from {} to {} in company {}",
                memberId, oldRole, newRole, companyOwnerId);
    }

    @Transactional
    public void removeMember(String adminEmail, Long companyOwnerId, Long memberId) {
        User admin = getUserByEmail(adminEmail);

        if (!hasMinimumRole(adminEmail, companyOwnerId, CompanyMember.MemberRole.ADMIN)) {
            throw new AccessDeniedException("Insufficient permissions");
        }

        CompanyMember member = companyMemberRepository
                .findByCompanyOwnerIdAndMemberUserId(companyOwnerId, memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Team member"));

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
                .orElseThrow(() -> new AccessDeniedException("No access to this company"));

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

        if (membership.getRole() == CompanyMember.MemberRole.ADMIN) {
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
    public CompanyMember.MemberRole checkAccess(String userEmail, Long companyOwnerId) {
        User user = getUserByEmail(userEmail);

        return companyMemberRepository.findRoleByCompanyOwnerIdAndMemberUserId(companyOwnerId, user.getId())
                .orElseThrow(() -> new AccessDeniedException("No access to this company"));
    }

    public boolean hasMinimumRole(String userEmail, Long companyOwnerId, CompanyMember.MemberRole minimumRole) {
        try {
            CompanyMember.MemberRole userRole = checkAccess(userEmail, companyOwnerId);
            return getRoleLevel(userRole) >= getRoleLevel(minimumRole);
        } catch (Exception e) {
            return false;
        }
    }

    // Helper methods

    private void checkExistingInvitation(Long ownerId, String email, String phone) {
        boolean exists = email != null
                ? invitationRepository.existsByCompanyOwnerIdAndInviteeEmailAndStatus(
                ownerId, email, Invitation.InvitationStatus.PENDING)
                : invitationRepository.existsByCompanyOwnerIdAndInviteePhoneAndStatus(
                ownerId, phone, Invitation.InvitationStatus.PENDING);

        if (exists) {
            throw new DuplicateResourceException("Active invitation already exists");
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
                throw new DuplicateResourceException("User is already a member");
            }
        }
    }

    private void validateInvitationStatus(Invitation invitation) {
        if (invitation.getStatus() != Invitation.InvitationStatus.PENDING) {
            throw new InvalidInvitationException("Invitation already used or cancelled");
        }

        if (invitation.isExpired()) {
            invitation.setStatus(Invitation.InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new InvalidInvitationException("Invitation expired");
        }
    }

    private void validateInvitationRecipient(Invitation invitation, User user) {
        boolean matches = (invitation.getInviteeEmail() != null &&
                invitation.getInviteeEmail().equalsIgnoreCase(user.getEmail())) ||
                (invitation.getInviteePhone() != null &&
                        invitation.getInviteePhone().equals(user.getPhone()));

        if (!matches) {
            throw new InvalidInvitationException("Invitation is for different user");
        }
    }

    private void validateNotAlreadyMember(Long companyOwnerId, Long userId) {
        if (companyMemberRepository.existsByCompanyOwnerIdAndMemberUserId(companyOwnerId, userId)) {
            throw new DuplicateResourceException("Already a member of this company");
        }
    }

    private int getRoleLevel(CompanyMember.MemberRole role) {
        return switch (role) {
            case ADMIN -> 3;
            case MODERATOR -> 2;
            case VIEWER -> 1;
        };
    }

    private void sendInvitationEmail(User owner, String recipientEmail, String invitationLink,
                                     CompanyMember.MemberRole role) {
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

    private String getRoleInEnglish(CompanyMember.MemberRole role) {
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