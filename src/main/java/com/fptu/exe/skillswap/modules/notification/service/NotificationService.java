package com.fptu.exe.skillswap.modules.notification.service;

import com.fptu.exe.skillswap.infrastructure.config.RealtimeOutboxProperties;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.notification.domain.Notification;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationRepository;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.dto.response.NotificationResponse;

import com.fptu.exe.skillswap.shared.cursor.CursorCodec;
import com.fptu.exe.skillswap.shared.cursor.CursorTokenPayload;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxEventTypes;
import com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxService;
import com.fptu.exe.skillswap.modules.notification.strategy.NotificationTitleRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserQueryPort userQueryPort;
    private final CursorCodec cursorCodec;
    private final DomainEventOutboxService domainEventOutboxService;
    private final RealtimeOutboxProperties realtimeOutboxProperties;
    private final NotificationTitleRegistry notificationTitleRegistry;

    @Transactional
    public void createNotification(UUID recipientUserId, NotificationType type, String title, String message,
            String relatedEntityType, UUID relatedEntityId) {
        createNotification(recipientUserId, type, title, message, relatedEntityType, relatedEntityId, null);
    }

    @Transactional
    public void createNotification(UUID recipientUserId,
            NotificationType type,
            String title,
            String message,
            String relatedEntityType,
            UUID relatedEntityId,
            String deepLink) {
        User recipient = userQueryPort.findUserById(recipientUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy người nhận"));

        Notification notification = Notification.builder()
                .recipientUser(recipient)
                .type(type)
                .title(normalizeTitle(type, title))
                .message(message)
                .relatedEntityType(relatedEntityType)
                .relatedEntityId(relatedEntityId)
                .deepLink(deepLink)
                .build();

        notification = notificationRepository.save(notification);
        long unreadCount = notificationRepository.countByRecipientUserIdAndReadAtIsNull(recipientUserId);

        enqueueNotificationOutbox(notification, unreadCount, "CREATED");
    }

    @Transactional
    public void upsertChatUnread(UUID recipientUserId, UUID conversationId) {
        // Chat notifications are excluded from general notification center and handled via /queue/chat/unread STOMP channel.
    }

    @Transactional
    public void clearChatUnread(UUID recipientUserId, UUID conversationId) {
        // Chat notifications are excluded from general notification center and handled via /queue/chat/unread STOMP channel.
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getMyNotifications(UUID currentUserId, boolean unreadOnly,
            Pageable pageable) {
        // Enforce page size cap to 50 to protect weak VPS
        int pageSize = Math.min(pageable.getPageSize(), 50);
        Pageable cappedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Notification> pageResult;
        if (unreadOnly) {
            pageResult = notificationRepository.findByRecipientUserIdAndReadAtIsNull(currentUserId, cappedPageable);
        } else {
            pageResult = notificationRepository.findByRecipientUserId(currentUserId, cappedPageable);
        }

        Page<NotificationResponse> dtoPage = pageResult.map(this::mapToResponse);
        return PageResponse.<NotificationResponse>builder()
                .content(dtoPage.getContent())
                .page(dtoPage.getNumber())
                .size(dtoPage.getSize())
                .totalElements(dtoPage.getTotalElements())
                .totalPages(dtoPage.getTotalPages())
                .last(dtoPage.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<NotificationResponse> getMyNotifications(UUID currentUserId,
            boolean unreadOnly,
            String cursor,
            Integer limit) {
        int resolvedLimit = defaultLimit(limit, 20);
        String filterHash = "notifications|recipient=" + currentUserId + "|unreadOnly=" + unreadOnly;
        DecodedNotificationCursor decodedCursor = decodeCursor(cursor, filterHash);
        List<Notification> notificationWindow = notificationRepository.findNotificationWindow(
                currentUserId,
                unreadOnly,
                decodedCursor.createdAt(),
                decodedCursor.notificationId(),
                resolvedLimit + 1);
        boolean hasNext = notificationWindow.size() > resolvedLimit;
        List<Notification> visibleNotifications = hasNext
                ? notificationWindow.subList(0, resolvedLimit)
                : notificationWindow;
        List<NotificationResponse> items = visibleNotifications.stream()
                .map(this::mapToResponse)
                .toList();
        String nextCursor = hasNext && !visibleNotifications.isEmpty()
                ? encodeNextCursor(visibleNotifications.get(visibleNotifications.size() - 1), filterHash)
                : null;
        return CursorPageResponse.<NotificationResponse>builder()
                .items(items)
                .nextCursor(nextCursor)
                .prevCursor(null)
                .hasNext(hasNext)
                .hasPrev(false)
                .limit(resolvedLimit)
                .build();
    }

    @Transactional(readOnly = true)
    public long getMyUnreadCount(UUID currentUserId) {
        return notificationRepository.countByRecipientUserIdAndReadAtIsNull(currentUserId);
    }

    @Transactional
    public void markAsRead(UUID currentUserId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndRecipientUserId(notificationId, currentUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND,
                        "Thông báo không tồn tại hoặc không thuộc quyền truy cập"));

        if (notification.getReadAt() == null) {
            notification.setReadAt(com.fptu.exe.skillswap.shared.util.DateTimeUtil.now());
            notificationRepository.save(notification);
            long unreadCount = notificationRepository.countByRecipientUserIdAndReadAtIsNull(currentUserId);

            enqueueNotificationBadgeOutbox(notification.getId(), currentUserId, unreadCount, "READ");
        }
        // If already read, idempotent behavior (do not fail, preserve old readAt)
    }

    @Transactional
    public void markAllAsRead(UUID currentUserId) {
        notificationRepository.markAllAsRead(currentUserId, com.fptu.exe.skillswap.shared.util.DateTimeUtil.now());
        long unreadCount = notificationRepository.countByRecipientUserIdAndReadAtIsNull(currentUserId);

        enqueueNotificationBadgeOutbox(currentUserId, currentUserId, unreadCount, "READ_ALL");
    }

    @Transactional(readOnly = true)
    public NotificationResponse getRealtimeNotification(UUID recipientUserId, UUID notificationId,
            String realtimeEventKind) {
        Notification notification = notificationRepository.findByIdAndRecipientUserId(notificationId, recipientUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND,
                        "Thông báo không tồn tại hoặc không thuộc quyền truy cập"));
        long unreadCount = notificationRepository.countByRecipientUserIdAndReadAtIsNull(recipientUserId);
        return mapToResponse(notification, unreadCount, realtimeEventKind);
    }

    private NotificationResponse mapToResponse(Notification notification) {
        return mapToResponse(notification, null, null);
    }

    private NotificationResponse mapToResponse(Notification notification, Long unreadCount, String realtimeEventKind) {
        String relatedEntityType = notification.getRelatedEntityType();
        UUID relatedEntityId = notification.getRelatedEntityId();

        String deepLink = notification.getDeepLink() == null ? "" : notification.getDeepLink();
        String actionType = "VIEW_DETAIL";

        if (!deepLink.isBlank()) {
            actionType = "VIEW_DETAIL";
            if ("BLOG_POST".equalsIgnoreCase(relatedEntityType)) {
                actionType = "VIEW_BLOG_POST";
            }
        } else if ("BOOKING".equalsIgnoreCase(relatedEntityType) && relatedEntityId != null) {
            deepLink = "/bookings/" + relatedEntityId;
            actionType = "VIEW_BOOKING";
        } else if ("CONVERSATION".equalsIgnoreCase(relatedEntityType) && relatedEntityId != null) {
            deepLink = "/chat/" + relatedEntityId;
            actionType = "OPEN_CHAT";
        } else if ("FORUM_POST".equalsIgnoreCase(relatedEntityType) && relatedEntityId != null) {
            deepLink = "/forum/posts/" + relatedEntityId;
            actionType = "VIEW_FORUM_POST";
        } else if ("BLOG_POST".equalsIgnoreCase(relatedEntityType) && relatedEntityId != null) {
            deepLink = "/blog/posts/" + relatedEntityId;
            actionType = "VIEW_BLOG_POST";
        } else if ("MENTOR_VERIFICATION".equalsIgnoreCase(relatedEntityType)) {
            deepLink = "/mentor/verification";
            actionType = "VIEW_MENTOR_VERIFICATION";
        }

        return NotificationResponse.builder()
                .notificationId(notification.getId())
                .type(notification.getType().name())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .relatedEntityType(relatedEntityType)
                .relatedEntityId(relatedEntityId)
                .deepLink(deepLink)
                .actionType(actionType)
                .read(notification.getReadAt() != null)
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .unreadCount(unreadCount)
                .realtimeEventKind(realtimeEventKind)
                .build();
    }

    private String normalizeTitle(NotificationType type, String fallbackTitle) {
        if (notificationTitleRegistry != null) {
            return notificationTitleRegistry.resolveTitle(type, fallbackTitle != null && !fallbackTitle.isBlank() ? fallbackTitle : "Thông báo mới");
        }
        return fallbackTitle != null && !fallbackTitle.isBlank() ? fallbackTitle : "Thông báo mới";
    }

    private int defaultLimit(Integer limit, int defaultValue) {
        int resolved = limit == null || limit <= 0 ? defaultValue : limit;
        return Math.min(resolved, 50);
    }

    private DecodedNotificationCursor decodeCursor(String cursor, String expectedFilterHash) {
        if (cursor == null || cursor.isBlank()) {
            return DecodedNotificationCursor.empty();
        }
        CursorTokenPayload payload = cursorCodec.decode(cursor);
        if (!expectedFilterHash.equals(payload.filterHash())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cursor không khớp với bộ lọc hiện tại");
        }
        if (payload.sortKey() == null || payload.secondaryKey() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cursor không hợp lệ");
        }
        try {
            return new DecodedNotificationCursor(
                    LocalDateTime.parse(payload.sortKey()),
                    UUID.fromString(payload.secondaryKey()));
        } catch (RuntimeException ex) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cursor chứa notification window không hợp lệ", ex);
        }
    }

    private String encodeNextCursor(Notification notification, String filterHash) {
        return cursorCodec.encode(CursorTokenPayload.builder()
                .sortKey(notification.getCreatedAt().toString())
                .secondaryKey(notification.getId().toString())
                .direction("NEXT")
                .filterHash(filterHash)
                .issuedAt(Instant.now())
                .build());
    }

    private record DecodedNotificationCursor(LocalDateTime createdAt, UUID notificationId) {
        private static DecodedNotificationCursor empty() {
            return new DecodedNotificationCursor(null, null);
        }
    }

    private void enqueueNotificationOutbox(Notification notification, long unreadCount, String eventKind) {
        if (!realtimeOutboxProperties.isEnabled()) {
            return;
        }
        domainEventOutboxService.enqueue(
                "NOTIFICATION",
                notification.getId(),
                DomainEventOutboxEventTypes.NOTIFICATION_CREATED,
                new NotificationCreatedPayload(notification.getId(), notification.getRecipientUser().getId(),
                        notification.getType().name(), eventKind, unreadCount));
        enqueueNotificationBadgeOutbox(notification.getId(), notification.getRecipientUser().getId(), unreadCount,
                eventKind);
    }

    private void enqueueNotificationBadgeOutbox(UUID aggregateId, UUID recipientUserId, long unreadCount,
            String eventKind) {
        if (!realtimeOutboxProperties.isEnabled()) {
            return;
        }
        domainEventOutboxService.enqueue(
                "NOTIFICATION",
                aggregateId,
                DomainEventOutboxEventTypes.NOTIFICATION_BADGE_UPDATED,
                new NotificationBadgePayload(recipientUserId, unreadCount, eventKind));
    }

    public record NotificationCreatedPayload(UUID notificationId, UUID recipientUserId, String type, String eventKind,
            long unreadCount) {
    }

    public record NotificationBadgePayload(UUID recipientUserId, long unreadCount, String eventKind) {
    }
}
