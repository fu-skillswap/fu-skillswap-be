package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.domain.AuditAction;
import com.fptu.exe.skillswap.modules.admin.domain.AuditLog;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminAuditLogListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminAuditLogItemResponse;
import com.fptu.exe.skillswap.modules.admin.repository.AuditLogRepository;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuditLogService {

    private static final List<String> ALLOWED_SORT_FIELDS = List.of("createdAt", "action", "entityType", "entityId");

    private final AuditLogRepository auditLogRepository;

    public PageResponse<AdminAuditLogItemResponse> getAuditLogs(AdminAuditLogListRequest request) {
        AdminAuditLogListRequest safeRequest = request == null ? new AdminAuditLogListRequest() : request;
        Page<AuditLog> page = auditLogRepository.searchForAdmin(
                safeRequest.getActorUserId(),
                normalizeBlankToNull(safeRequest.getEntityType()),
                safeRequest.getEntityId(),
                parseAction(safeRequest.getAction()),
                safeRequest.getFrom(),
                safeRequest.getTo(),
                buildPageable(safeRequest)
        );

        return PageResponse.<AdminAuditLogItemResponse>builder()
                .content(page.getContent().stream().map(this::toResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    private AdminAuditLogItemResponse toResponse(AuditLog auditLog) {
        return new AdminAuditLogItemResponse(
                auditLog.getId(),
                auditLog.getCreatedAt(),
                auditLog.getActor() == null ? null : auditLog.getActor().getId(),
                auditLog.getActor() == null ? null : auditLog.getActor().getFullName(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getAction().name(),
                auditLog.getOldValue(),
                auditLog.getNewValue(),
                auditLog.getIpAddress(),
                auditLog.getUserAgent()
        );
    }

    private Pageable buildPageable(AdminAuditLogListRequest request) {
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

    private AuditAction parseAction(String action) {
        if (action == null || action.isBlank()) {
            return null;
        }
        try {
            return AuditAction.valueOf(action.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "action không hợp lệ");
        }
    }

    private String normalizeBlankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
