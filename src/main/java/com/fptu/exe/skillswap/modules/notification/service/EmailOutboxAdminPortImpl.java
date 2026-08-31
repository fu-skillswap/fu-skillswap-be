package com.fptu.exe.skillswap.modules.notification.service;

import com.fptu.exe.skillswap.modules.notification.domain.EmailOutbox;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationStatus;
import com.fptu.exe.skillswap.modules.notification.port.EmailOutboxAdminDetail;
import com.fptu.exe.skillswap.modules.notification.port.EmailOutboxAdminPort;
import com.fptu.exe.skillswap.modules.notification.port.EmailOutboxAdminQuery;
import com.fptu.exe.skillswap.modules.notification.port.EmailOutboxAdminSummary;
import com.fptu.exe.skillswap.modules.notification.port.EmailOutboxRetryResult;
import com.fptu.exe.skillswap.modules.notification.repository.EmailOutboxRepository;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class EmailOutboxAdminPortImpl implements EmailOutboxAdminPort {
    private final EmailOutboxRepository repository;

    @Override
    public boolean existsById(UUID emailOutboxId) {
        return emailOutboxId != null && repository.existsById(emailOutboxId);
    }

    @Override
    public PageResponse<EmailOutboxAdminSummary> search(EmailOutboxAdminQuery query) {
        EmailOutboxAdminQuery safe = query == null ? new EmailOutboxAdminQuery(null, null, null, null, null, 0, 20, null, null) : query;
        int page = Math.max(0, safe.page());
        int size = Math.min(Math.max(1, safe.size()), 100);
        String sort = switch (safe.sortBy()) { case "sentAt", "status", "retryCount", "toEmail", "templateCode" -> safe.sortBy(); default -> "createdAt"; };
        Sort.Direction direction;
        try { direction = Sort.Direction.valueOf(safe.direction() == null ? "ASC" : safe.direction().trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { direction = "createdAt".equals(sort) ? Sort.Direction.ASC : Sort.Direction.DESC; }
        var result = repository.searchForAdmin(status(safe.status()), blank(safe.templateCode()), like(safe.toEmail()),
                safe.from(), safe.to(), PageRequest.of(page, size, Sort.by(direction, sort)));
        return PageResponse.<EmailOutboxAdminSummary>builder().content(result.getContent().stream().map(this::summary).toList())
                .page(result.getNumber()).size(result.getSize()).totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages()).last(result.isLast()).build();
    }

    @Override public EmailOutboxAdminDetail get(UUID id) { return detail(require(id)); }

    @Override @Transactional
    public EmailOutboxRetryResult retryFailed(UUID id) {
        EmailOutbox email = repository.findByIdForUpdate(id).orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy email outbox"));
        if (email.getStatus() != NotificationStatus.FAILED) throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể retry email đang FAILED");
        String previousStatus = email.getStatus().name(); Integer previousRetryCount = email.getRetryCount(); String previousLastError = email.getLastError();
        email.setStatus(NotificationStatus.PENDING); email.setRetryCount((email.getRetryCount() == null ? 0 : email.getRetryCount()) + 1);
        email.setLastError(null); email.setSentAt(null);
        return new EmailOutboxRetryResult(detail(repository.save(email)), previousStatus, previousRetryCount, previousLastError);
    }

    @Override
    public List<String> statusNames() {
        return java.util.Arrays.stream(NotificationStatus.values()).map(Enum::name).toList();
    }

    private EmailOutbox require(UUID id) { return repository.findById(id).orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy email outbox")); }
    private EmailOutboxAdminSummary summary(EmailOutbox e) { return new EmailOutboxAdminSummary(e.getId(), e.getToEmail(), e.getSubject(), e.getTemplateCode(), e.getStatus().name(), e.getRetryCount(), e.getCreatedAt(), e.getSentAt(), e.getLastError()); }
    private EmailOutboxAdminDetail detail(EmailOutbox e) { return new EmailOutboxAdminDetail(e.getId(), e.getToEmail(), e.getSubject(), e.getTemplateCode(), e.getStatus().name(), e.getRetryCount(), e.getCreatedAt(), e.getSentAt(), e.getBody(), e.getLastError()); }
    private NotificationStatus status(String raw) { if (raw == null || raw.isBlank()) return null; try { return NotificationStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ex) { throw new BaseException(ErrorCode.BAD_REQUEST, "status không hợp lệ"); } }
    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String like(String value) { return value == null || value.isBlank() ? null : "%" + value.trim().toLowerCase(Locale.ROOT) + "%"; }
}
