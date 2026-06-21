package com.fptu.exe.skillswap.modules.notification.service;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.notification.domain.Notification;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationRepository;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.dto.response.NotificationResponse;
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

    @Transactional
    public void createNotification(UUID recipientUserId, NotificationType type, String title, String message, String relatedEntityType, UUID relatedEntityId) {
        User recipient = userRepository.findById(recipientUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy người nhận"));

        Notification notification = Notification.builder()
                .recipientUser(recipient)
                .type(type)
                .title(title)
                .message(message)
                .relatedEntityType(relatedEntityType)
                .relatedEntityId(relatedEntityId)
                .build();

        notificationRepository.save(notification);
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
        }
        // If already read, idempotent behavior (do not fail, preserve old readAt)
    }

    @Transactional
    public void markAllAsRead(UUID currentUserId) {
        notificationRepository.markAllAsRead(currentUserId, LocalDateTime.now());
    }

    private NotificationResponse mapToResponse(Notification notification) {
        return NotificationResponse.builder()
                .notificationId(notification.getId())
                .type(notification.getType().name())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .relatedEntityType(notification.getRelatedEntityType())
                .relatedEntityId(notification.getRelatedEntityId())
                .read(notification.getReadAt() != null)
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
