package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminEmailOutboxListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminEmailOutboxDetailResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminEmailOutboxItemResponse;
import com.fptu.exe.skillswap.modules.admin.domain.AdminCaseActivityEventType;
import com.fptu.exe.skillswap.modules.notification.domain.EmailOutbox;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationStatus;
import com.fptu.exe.skillswap.modules.notification.repository.EmailOutboxRepository;
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

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminEmailOutboxService {

    private static final int ERROR_PREVIEW_LIMIT = 160;
    private static final List<String> ALLOWED_SORT_FIELDS = List.of("createdAt", "sentAt", "status", "retryCount", "toEmail", "templateCode");

    private final EmailOutboxRepository emailOutboxRepository;
    private final AdminCaseSupportService adminCaseSupportService;
    private final AdminAuditWriterService adminAuditWriterService;

    public PageResponse<AdminEmailOutboxItemResponse> getEmailOutbox(AdminEmailOutboxListRequest request) {
        AdminEmailOutboxListRequest safeRequest = request == null ? new AdminEmailOutboxListRequest() : request;
        Page<EmailOutbox> page = emailOutboxRepository.searchForAdmin(
                parseStatus(safeRequest.getStatus()),
                normalizeBlankToNull(safeRequest.getTemplateCode()),
                normalizeLikePattern(safeRequest.getToEmail()),
                safeRequest.getFrom(),
                safeRequest.getTo(),
                buildPageable(safeRequest)
        );

        return PageResponse.<AdminEmailOutboxItemResponse>builder()
                .content(page.getContent().stream().map(this::toListResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    public AdminEmailOutboxDetailResponse getEmailOutboxDetail(UUID emailOutboxId) {
        EmailOutbox emailOutbox = emailOutboxRepository.findById(emailOutboxId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy email outbox"));
        return toDetailResponse(emailOutbox);
    }

    @Transactional
    public AdminEmailOutboxDetailResponse retry(UUID emailOutboxId, UUID adminUserId) {
        adminCaseSupportService.requireAdminUser(adminUserId);
        EmailOutbox emailOutbox = emailOutboxRepository.findByIdForUpdate(emailOutboxId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy email outbox"));
        if (emailOutbox.getStatus() != NotificationStatus.FAILED) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể retry email đang FAILED");
        }

        String previousStatus = emailOutbox.getStatus().name();
        Integer previousRetryCount = emailOutbox.getRetryCount();
        String previousLastError = emailOutbox.getLastError();
        emailOutbox.setStatus(NotificationStatus.PENDING);
        emailOutbox.setRetryCount((emailOutbox.getRetryCount() == null ? 0 : emailOutbox.getRetryCount()) + 1);
        emailOutbox.setLastError(null);
        emailOutbox.setSentAt(null);
        emailOutboxRepository.save(emailOutbox);

        adminAuditWriterService.writeOperatorEvent(
                adminUserId,
                "EMAIL_OUTBOX",
                emailOutboxId,
                AdminCaseActivityEventType.EMAIL_RETRY_REQUESTED.name(),
                java.util.Map.of(
                        "status", previousStatus,
                        "retryCount", previousRetryCount == null ? 0 : previousRetryCount,
                        "lastError", previousLastError == null ? "" : previousLastError
                ),
                java.util.Map.of(
                        "status", emailOutbox.getStatus().name(),
                        "retryCount", emailOutbox.getRetryCount()
                )
        );
        return toDetailResponse(emailOutbox);
    }

    private AdminEmailOutboxItemResponse toListResponse(EmailOutbox emailOutbox) {
        return new AdminEmailOutboxItemResponse(
                emailOutbox.getId(),
                emailOutbox.getToEmail(),
                emailOutbox.getSubject(),
                emailOutbox.getTemplateCode(),
                emailOutbox.getStatus().name(),
                emailOutbox.getRetryCount(),
                emailOutbox.getCreatedAt(),
                emailOutbox.getSentAt(),
                toErrorPreview(emailOutbox.getLastError())
        );
    }

    private AdminEmailOutboxDetailResponse toDetailResponse(EmailOutbox emailOutbox) {
        return new AdminEmailOutboxDetailResponse(
                emailOutbox.getId(),
                emailOutbox.getToEmail(),
                emailOutbox.getSubject(),
                emailOutbox.getTemplateCode(),
                emailOutbox.getStatus().name(),
                emailOutbox.getRetryCount(),
                emailOutbox.getCreatedAt(),
                emailOutbox.getSentAt(),
                toErrorPreview(emailOutbox.getLastError()),
                emailOutbox.getBody(),
                emailOutbox.getLastError()
        );
    }

    private Pageable buildPageable(AdminEmailOutboxListRequest request) {
        int page = Math.max(request.getPage(), 0);
        int size = Math.min(Math.max(request.getSize(), 1), 100);
        String sortBy = resolveSortBy(request.getSortBy());
        Sort.Direction direction = request.resolveDirection();
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }

    private String resolveSortBy(String sortBy) {
        if (sortBy == null || !ALLOWED_SORT_FIELDS.contains(sortBy)) {
            return "createdAt";
        }
        return sortBy;
    }

    private NotificationStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return NotificationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "status không hợp lệ");
        }
    }

    private String normalizeBlankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeLikePattern(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private String toErrorPreview(String lastError) {
        if (lastError == null || lastError.isBlank()) {
            return null;
        }
        if (lastError.length() <= ERROR_PREVIEW_LIMIT) {
            return lastError;
        }
        return lastError.substring(0, ERROR_PREVIEW_LIMIT - 3) + "...";
    }
}
