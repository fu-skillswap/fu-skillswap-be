package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminEmailOutboxListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminEmailOutboxDetailResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminEmailOutboxItemResponse;
import com.fptu.exe.skillswap.modules.admin.domain.AdminCaseActivityEventType;
import com.fptu.exe.skillswap.modules.notification.port.EmailOutboxAdminDetail;
import com.fptu.exe.skillswap.modules.notification.port.EmailOutboxAdminPort;
import com.fptu.exe.skillswap.modules.notification.port.EmailOutboxAdminQuery;
import com.fptu.exe.skillswap.modules.notification.port.EmailOutboxAdminSummary;
import com.fptu.exe.skillswap.modules.notification.port.EmailOutboxRetryResult;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminEmailOutboxService {

    private static final int ERROR_PREVIEW_LIMIT = 160;
    private final EmailOutboxAdminPort emailOutboxAdminPort;
    private final AdminCaseSupportService adminCaseSupportService;
    private final AdminAuditWriterService adminAuditWriterService;

    public PageResponse<AdminEmailOutboxItemResponse> getEmailOutbox(AdminEmailOutboxListRequest request) {
        AdminEmailOutboxListRequest safeRequest = request == null ? new AdminEmailOutboxListRequest() : request;
        PageResponse<EmailOutboxAdminSummary> page = emailOutboxAdminPort.search(query(safeRequest));
        return PageResponse.<AdminEmailOutboxItemResponse>builder().content(page.getContent().stream().map(this::toListResponse).toList())
                .page(page.getPage()).size(page.getSize()).totalElements(page.getTotalElements()).totalPages(page.getTotalPages()).last(page.isLast()).build();
    }

    public AdminEmailOutboxDetailResponse getEmailOutboxDetail(UUID emailOutboxId) {
        return toDetailResponse(emailOutboxAdminPort.get(emailOutboxId));
    }

    @Transactional
    public AdminEmailOutboxDetailResponse retry(UUID emailOutboxId, UUID adminUserId) {
        adminCaseSupportService.requireAdminUser(adminUserId);
        EmailOutboxRetryResult result = emailOutboxAdminPort.retryFailed(emailOutboxId);

        adminAuditWriterService.writeOperatorEvent(
                adminUserId,
                "EMAIL_OUTBOX",
                emailOutboxId,
                AdminCaseActivityEventType.EMAIL_RETRY_REQUESTED.name(),
                java.util.Map.of(
                        "status", result.previousStatus(),
                        "retryCount", result.previousRetryCount() == null ? 0 : result.previousRetryCount(),
                        "lastError", result.previousLastError() == null ? "" : result.previousLastError()
                ),
                java.util.Map.of(
                        "status", result.email().status(),
                        "retryCount", result.email().retryCount()
                )
        );
        return toDetailResponse(result.email());
    }

    private AdminEmailOutboxItemResponse toListResponse(EmailOutboxAdminSummary emailOutbox) {
        return new AdminEmailOutboxItemResponse(
                emailOutbox.emailOutboxId(), emailOutbox.toEmail(), emailOutbox.subject(), emailOutbox.templateCode(),
                emailOutbox.status(), emailOutbox.retryCount(), emailOutbox.createdAt(), emailOutbox.sentAt(), toErrorPreview(emailOutbox.lastError())
        );
    }

    private AdminEmailOutboxDetailResponse toDetailResponse(EmailOutboxAdminDetail emailOutbox) {
        return new AdminEmailOutboxDetailResponse(
                emailOutbox.emailOutboxId(), emailOutbox.toEmail(), emailOutbox.subject(), emailOutbox.templateCode(),
                emailOutbox.status(), emailOutbox.retryCount(), emailOutbox.createdAt(), emailOutbox.sentAt(),
                toErrorPreview(emailOutbox.lastError()), emailOutbox.body(), emailOutbox.lastError()
        );
    }

    private EmailOutboxAdminQuery query(AdminEmailOutboxListRequest request) {
        return new EmailOutboxAdminQuery(request.getStatus(), request.getTemplateCode(), request.getToEmail(), request.getFrom(), request.getTo(),
                request.getPage(), request.getSize(), request.getSortBy(), request.getDirection());
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
