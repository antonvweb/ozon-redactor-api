package org.ozonLabel.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.domain.model.Notification;
import org.ozonLabel.domain.model.User;
import org.ozonLabel.domain.repository.NotificationRepository;
import org.ozonLabel.domain.repository.UserRepository;
import org.ozonLabel.user.dto.CreateNotificationDto;
import org.ozonLabel.user.dto.NotificationListResponseDto;
import org.ozonLabel.user.dto.NotificationResponseDto;
import org.ozonLabel.user.dto.NotificationStatsDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    /**
     * Создать уведомление
     */
    @Transactional
    public Notification createNotification(CreateNotificationDto dto) {
        Notification notification = Notification.builder()
                .userId(dto.getUserId())
                .type(dto.getType())
                .title(dto.getTitle())
                .message(dto.getMessage())
                .data(dto.getData())
                .priority(dto.getPriority() != null ? dto.getPriority() : Notification.NotificationPriority.NORMAL)
                .actionType(dto.getActionType())
                .actionUrl(dto.getActionUrl())
                .relatedEntityType(dto.getRelatedEntityType())
                .relatedEntityId(dto.getRelatedEntityId())
                .senderId(dto.getSenderId())
                .expiresAt(dto.getExpiresAt())
                .isRead(false)
                .isArchived(false)
                .build();

        notification = notificationRepository.save(notification);

        log.info("Создано уведомление {} для пользователя {}", notification.getId(), dto.getUserId());

        return notification;
    }

    /**
     * Создать уведомление о приглашении
     */
    @Transactional
    public Notification createInvitationNotification(Long recipientId, Long invitationId,
                                                     Long senderId, String senderName,
                                                     String companyName, String role) {
        Map<String, Object> data = new HashMap<>();
        data.put("invitationId", invitationId);
        data.put("senderName", senderName);
        data.put("companyName", companyName);
        data.put("role", role);

        CreateNotificationDto dto = CreateNotificationDto.builder()
                .userId(recipientId)
                .type(Notification.NotificationType.INVITATION)
                .title("Приглашение в компани")
                .message(String.format("%s приглашает вас в компанию \"%s\" с ролью %s",
                        senderName, companyName, role))
                .data(data)
                .priority(Notification.NotificationPriority.HIGH)
                .actionType("INVITATION_RESPONSE")
                .relatedEntityType("INVITATION")
                .relatedEntityId(invitationId)
                .senderId(senderId)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        return createNotification(dto);
    }

    /**
     * Создать уведомление о принятии приглашения
     */
    @Transactional
    public Notification createInvitationAcceptedNotification(Long ownerId, String memberName,
                                                             Long memberId, String companyName) {
        Map<String, Object> data = new HashMap<>();
        data.put("memberName", memberName);
        data.put("memberId", memberId);
        data.put("companyName", companyName);

        CreateNotificationDto dto = CreateNotificationDto.builder()
                .userId(ownerId)
                .type(Notification.NotificationType.INVITATION_ACCEPTED)
                .title("Приглашение принято")
                .message(String.format("%s принял приглашение и присоединился к компании \"%s\"",
                        memberName, companyName))
                .data(data)
                .priority(Notification.NotificationPriority.NORMAL)
                .relatedEntityType("MEMBER")
                .relatedEntityId(memberId)
                .senderId(memberId)
                .build();

        return createNotification(dto);
    }

    /**
     * Создать уведомление об отклонении приглашения
     */
    @Transactional
    public Notification createInvitationRejectedNotification(Long ownerId, String memberName,
                                                             String companyName) {
        Map<String, Object> data = new HashMap<>();
        data.put("memberName", memberName);
        data.put("companyName", companyName);

        CreateNotificationDto dto = CreateNotificationDto.builder()
                .userId(ownerId)
                .type(Notification.NotificationType.INVITATION_REJECTED)
                .title("Приглашение отклонено")
                .message(String.format("%s отклонил приглашение в компанию \"%s\"",
                        memberName, companyName))
                .data(data)
                .priority(Notification.NotificationPriority.NORMAL)
                .build();

        return createNotification(dto);
    }

    /**
     * Создать системное уведомление
     */
    @Transactional
    public Notification createSystemNotification(Long userId, String title, String message) {
        CreateNotificationDto dto = CreateNotificationDto.builder()
                .userId(userId)
                .type(Notification.NotificationType.SYSTEM)
                .title(title)
                .message(message)
                .priority(Notification.NotificationPriority.NORMAL)
                .build();

        return createNotification(dto);
    }

    /**
     * Получить все уведомления пользователя
     */
    public NotificationListResponseDto getUserNotifications(String userEmail, int page, int size,
                                                            Boolean unreadOnly, String type) {
        User user = getUserByEmail(userEmail);
        Pageable pageable = PageRequest.of(page, size);

        Page<Notification> notificationPage;

        if (unreadOnly != null && unreadOnly) {
            notificationPage = notificationRepository
                    .findByUserIdAndIsReadFalseOrderByCreatedAtDesc(user.getId(), pageable);
        } else if (type != null && !type.isEmpty()) {
            Notification.NotificationType notifType = Notification.NotificationType.valueOf(type);
            notificationPage = notificationRepository
                    .findByUserIdAndTypeOrderByCreatedAtDesc(user.getId(), notifType, pageable);
        } else {
            notificationPage = notificationRepository
                    .findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
        }

        List<NotificationResponseDto> notifications = notificationPage.getContent().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        Long unreadCount = notificationRepository.countByUserIdAndIsReadFalse(user.getId());

        return NotificationListResponseDto.builder()
                .notifications(notifications)
                .unreadCount(unreadCount)
                .currentPage(notificationPage.getNumber())
                .totalPages(notificationPage.getTotalPages())
                .totalElements(notificationPage.getTotalElements())
                .build();
    }

    /**
     * Получить непрочитанные уведомления
     */
    public List<NotificationResponseDto> getUnreadNotifications(String userEmail) {
        User user = getUserByEmail(userEmail);

        List<Notification> notifications = notificationRepository
                .findByUserIdAndIsReadFalseOrderByCreatedAtDesc(user.getId());

        return notifications.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Отметить уведомление как прочитанное
     */
    @Transactional
    public void markAsRead(String userEmail, Long notificationId) {
        User user = getUserByEmail(userEmail);

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Уведомление не найдено"));

        if (!notification.getUserId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к этому уведомлению");
        }

        if (!notification.getIsRead()) {
            notification.markAsRead();
            notificationRepository.save(notification);
            log.info("Уведомление {} отмечено как прочитанное пользователем {}", notificationId, userEmail);
        }
    }

    /**
     * Массово отметить как прочитанные
     */
    @Transactional
    public int markMultipleAsRead(String userEmail, List<Long> notificationIds) {
        User user = getUserByEmail(userEmail);
        return notificationRepository.markAsRead(notificationIds, user.getId(), LocalDateTime.now());
    }

    /**
     * Отметить все как прочитанные
     */
    @Transactional
    public int markAllAsRead(String userEmail) {
        User user = getUserByEmail(userEmail);
        List<Notification> unreadNotifications = notificationRepository
                .findByUserIdAndIsReadFalseOrderByCreatedAtDesc(user.getId());

        List<Long> ids = unreadNotifications.stream()
                .map(Notification::getId)
                .collect(Collectors.toList());

        if (ids.isEmpty()) {
            return 0;
        }

        return notificationRepository.markAsRead(ids, user.getId(), LocalDateTime.now());
    }

    /**
     * Удалить уведомление
     */
    @Transactional
    public void deleteNotification(String userEmail, Long notificationId) {
        User user = getUserByEmail(userEmail);

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Уведомление не найдено"));

        if (!notification.getUserId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к этому уведомлению");
        }

        notificationRepository.delete(notification);
        log.info("Уведомление {} удалено пользователем {}", notificationId, userEmail);
    }

    /**
     * Массово удалить уведомления
     */
    @Transactional
    public int deleteMultiple(String userEmail, List<Long> notificationIds) {
        User user = getUserByEmail(userEmail);
        return notificationRepository.deleteByIds(notificationIds, user.getId());
    }

    /**
     * Получить количество непрочитанных уведомлений
     */
    public Long getUnreadCount(String userEmail) {
        User user = getUserByEmail(userEmail);
        return notificationRepository.countByUserIdAndIsReadFalse(user.getId());
    }

    /**
     * Получить статистику уведомлений
     */
    public NotificationStatsDto getNotificationStats(String userEmail) {
        User user = getUserByEmail(userEmail);

        Long totalUnread = notificationRepository.countByUserIdAndIsReadFalse(user.getId());
        Long invitations = notificationRepository.countByUserIdAndType(user.getId(), Notification.NotificationType.INVITATION);
        Long system = notificationRepository.countByUserIdAndType(user.getId(), Notification.NotificationType.SYSTEM);
        Long support = notificationRepository.countByUserIdAndType(user.getId(), Notification.NotificationType.SUPPORT);

        List<Notification> urgent = notificationRepository
                .findByUserIdAndPriorityOrderByCreatedAtDesc(user.getId(), Notification.NotificationPriority.URGENT);

        return NotificationStatsDto.builder()
                .totalUnread(totalUnread)
                .totalInvitations(invitations)
                .totalSystem(system)
                .totalSupport(support)
                .urgentCount((long) urgent.size())
                .lastChecked(LocalDateTime.now())
                .build();
    }

    /**
     * Удалить истекшие уведомления (для scheduled task)
     */
    @Transactional
    public int cleanupExpiredNotifications() {
        return notificationRepository.deleteExpiredNotifications(LocalDateTime.now());
    }

    private NotificationResponseDto mapToDto(Notification notification) {
        NotificationResponseDto.SenderInfo senderInfo = null;

        if (notification.getSenderId() != null) {
            User sender = userRepository.findById(notification.getSenderId()).orElse(null);
            if (sender != null) {
                senderInfo = NotificationResponseDto.SenderInfo.builder()
                        .id(sender.getId())
                        .name(sender.getName())
                        .email(sender.getEmail())
                        .build();
            }
        }

        return NotificationResponseDto.builder()
                .id(notification.getId())
                .type(notification.getType().name())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .data(notification.getData())
                .isRead(notification.getIsRead())
                .isArchived(notification.getIsArchived())
                .priority(notification.getPriority().name())
                .actionType(notification.getActionType())
                .actionUrl(notification.getActionUrl())
                .relatedEntityType(notification.getRelatedEntityType())
                .relatedEntityId(notification.getRelatedEntityId())
                .sender(senderInfo)
                .expiresAt(notification.getExpiresAt())
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .build();
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));
    }
}