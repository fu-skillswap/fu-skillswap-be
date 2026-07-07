package com.fptu.exe.skillswap.modules.notification.service;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.notification.domain.Notification;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationRepository;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.dto.response.NotificationResponse;
import com.fptu.exe.skillswap.modules.notification.event.NotificationBadgeChangedEvent;
import com.fptu.exe.skillswap.modules.notification.event.NotificationCreatedEvent;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Transactional
    public void createNotification(UUID recipientUserId, NotificationType type, String title, String message, String relatedEntityType, UUID relatedEntityId) {
        User recipient = userRepository.findById(recipientUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy người nhận"));

        Notification notification = Notification.builder()
                .recipientUser(recipient)
                .type(type)
                .title(normalizeTitle(type, title))
                .message(message)
                .relatedEntityType(relatedEntityType)
                .relatedEntityId(relatedEntityId)
                .build();

        notification = notificationRepository.save(notification);
        long unreadCount = notificationRepository.countByRecipientUserIdAndReadAtIsNull(recipientUserId);
        eventPublisher.publishEvent(new NotificationCreatedEvent(
                recipientUserId,
                mapToResponse(notification, unreadCount, "CREATED"),
                type
        ));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getMyNotifications(UUID currentUserId, boolean unreadOnly, Pageable pageable) {
        // Enforce page size cap to 50 to protect weak VPS
        int pageSize = Math.min(pageable.getPageSize(), 50);
        Pageable cappedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

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
    public long getMyUnreadCount(UUID currentUserId) {
        return notificationRepository.countByRecipientUserIdAndReadAtIsNull(currentUserId);
    }

    @Transactional
    public void markAsRead(UUID currentUserId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndRecipientUserId(notificationId, currentUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Thông báo không tồn tại hoặc không thuộc quyền truy cập"));

        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            notificationRepository.save(notification);
            long unreadCount = notificationRepository.countByRecipientUserIdAndReadAtIsNull(currentUserId);
            eventPublisher.publishEvent(new NotificationBadgeChangedEvent(currentUserId, unreadCount, "READ"));
        }
        // If already read, idempotent behavior (do not fail, preserve old readAt)
    }

    @Transactional
    public void markAllAsRead(UUID currentUserId) {
        notificationRepository.markAllAsRead(currentUserId, LocalDateTime.now());
        long unreadCount = notificationRepository.countByRecipientUserIdAndReadAtIsNull(currentUserId);
        eventPublisher.publishEvent(new NotificationBadgeChangedEvent(currentUserId, unreadCount, "READ_ALL"));
    }

    private NotificationResponse mapToResponse(Notification notification) {
        return mapToResponse(notification, null, null);
    }

    private NotificationResponse mapToResponse(Notification notification, Long unreadCount, String realtimeEventKind) {
        String relatedEntityType = notification.getRelatedEntityType();
        UUID relatedEntityId = notification.getRelatedEntityId();

        String deepLink = "";
        String actionType = "VIEW_DETAIL";

        if ("BOOKING".equalsIgnoreCase(relatedEntityType) && relatedEntityId != null) {
            deepLink = "/bookings/" + relatedEntityId;
            actionType = "VIEW_BOOKING";
        } else if ("CONVERSATION".equalsIgnoreCase(relatedEntityType) && relatedEntityId != null) {
            deepLink = "/chat/" + relatedEntityId;
            actionType = "OPEN_CHAT";
        } else if ("FORUM_POST".equalsIgnoreCase(relatedEntityType) && relatedEntityId != null) {
            deepLink = "/forum/posts/" + relatedEntityId;
            actionType = "VIEW_FORUM_POST";
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
        if (type == null) {
            return fallbackTitle;
        }
        return switch (type) {
            case MENTOR_VERIFICATION_APPROVED -> "Hồ sơ mentor được duyệt";
            case MENTOR_VERIFICATION_REJECTED -> "Hồ sơ mentor bị từ chối";
            case MENTOR_VERIFICATION_NEEDS_REVISION -> "Cần bổ sung hồ sơ mentor";
            case BOOKING_REQUEST_CREATED -> "Có yêu cầu mentoring mới";
            case BOOKING_ACCEPTED -> "Mentor đã nhận lịch";
            case BOOKING_PAYMENT_CONFIRMED -> "Thanh toán đã xác nhận";
            case BOOKING_PAYMENT_EXPIRED -> "Hết hạn thanh toán";
            case BOOKING_REJECTED -> "Yêu cầu mentoring bị từ chối";
            case BOOKING_CANCELLED_BY_MENTEE -> "Mentee đã hủy lịch";
            case BOOKING_CANCELLED_BY_MENTOR -> "Mentor đã hủy lịch";
            case BOOKING_AUTO_REJECTED -> "Yêu cầu đã tự hủy";
            case BOOKING_REQUEST_EXPIRED -> "Yêu cầu mentoring đã hết hạn";
            case BOOKING_RESCHEDULE_REQUESTED -> "Có yêu cầu đổi lịch";
            case BOOKING_RESCHEDULE_ACCEPTED -> "Đề nghị đổi lịch đã duyệt";
            case BOOKING_RESCHEDULE_REJECTED -> "Đề nghị đổi lịch bị từ chối";
            case BOOKING_RESCHEDULE_EXPIRED -> "Yêu cầu đổi lịch hết hạn";
            case MEETING_LINK_UPDATED -> "Link buổi học đã cập nhật";
            case SESSION_COMPLETED -> "Phiên mentoring đã hoàn thành";
            case FEEDBACK_RECEIVED -> "Bạn vừa nhận đánh giá mới";
            case FORUM_POST_COMMENTED -> "Bài viết có bình luận mới";
            case FORUM_POST_HIDDEN -> "Bài viết đã bị ẩn";
            case FORUM_COMMENT_HIDDEN -> "Bình luận đã bị ẩn";
            case ACCOUNT_UNLOCKED -> "Tài khoản đã được mở khóa";
        };
    }
}
