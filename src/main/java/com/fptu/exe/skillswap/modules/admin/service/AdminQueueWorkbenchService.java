package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.domain.AdminCaseType;
import com.fptu.exe.skillswap.modules.admin.domain.AdminQueueKey;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminQueueCaseListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminQueueCaseItemResponse;
import com.fptu.exe.skillswap.modules.admin.repository.AdminQueueQueryRepository;
import com.fptu.exe.skillswap.modules.admin.strategy.AdminQueueDescriptorRegistry;
import com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTime;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminQueueWorkbenchService {

    private static final List<String> ALLOWED_SORT_FIELDS = List.of("createdAt", "updatedAt", "status", "title");

    private final AdminQueueQueryRepository adminQueueQueryRepository;
    private final AdminQueueDescriptorRegistry adminQueueDescriptorRegistry;

    public PageResponse<AdminQueueCaseItemResponse> getQueueItems(UUID adminUserId, AdminQueueCaseListRequest request) {
        AdminQueueCaseListRequest safeRequest = request == null ? new AdminQueueCaseListRequest() : request;
        if (Boolean.TRUE.equals(safeRequest.getAssignedToMe())
                && Boolean.TRUE.equals(safeRequest.getUnassignedOnly())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "assignedToMe và unassignedOnly không thể cùng bật");
        }

        AdminQueueKey queueKey = AdminQueueKey.parse(safeRequest.getQueueKey());
        Pageable pageable = buildPageable(safeRequest);
        PageImpl<AdminQueueQueryRepository.QueueCaseRow> page = adminQueueQueryRepository.findQueueItems(
                queueKey,
                adminUserId,
                safeRequest.getAssignedToMe(),
                safeRequest.getUnassignedOnly(),
                pageable);

        List<AdminQueueCaseItemResponse> content = page.getContent().stream()
                .map(row -> toResponse(queueKey, row))
                .toList();

        return PageResponse.<AdminQueueCaseItemResponse>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    private AdminQueueCaseItemResponse toResponse(AdminQueueKey queueKey, AdminQueueQueryRepository.QueueCaseRow row) {
        LocalDateTime createdAt = row.createdAt();
        LocalDateTime now = DateTimeUtil.now();
        long ageMinutes = createdAt == null ? 0L : Math.max(0L, Duration.between(createdAt, now).toMinutes());
        LocalDateTime responseDeadline = toBusinessTime(
                BookingDeadlinePolicy.resolveIssueResponseDeadlineUtc(BookingTime.toInstant(row.issueSubmittedAt())));
        LocalDateTime adminDeadline = toBusinessTime(
                BookingDeadlinePolicy.resolveAdminDisputeSlaDeadlineUtc(BookingTime.toInstant(row.adminEscalatedAt())));
        LocalDateTime autoReleaseAt = toBusinessTime(BookingDeadlinePolicy
                .resolveAdminDisputeAutoReleaseDeadlineUtc(BookingTime.toInstant(row.adminSlaOverdueAt())));
        var disputeSlaStatusValue = BookingDeadlinePolicy.resolveDisputeSlaStatus(
                BookingTime.toInstant(row.issueSubmittedAt()),
                BookingTime.toInstant(row.adminEscalatedAt()),
                BookingTime.toInstant(row.adminSlaOverdueAt()),
                null);
        String disputeSlaStatus = disputeSlaStatusValue == null ? null : disputeSlaStatusValue.name();
        LocalDateTime activeDeadline = row.adminSlaOverdueAt() != null ? autoReleaseAt
                : row.adminEscalatedAt() != null ? adminDeadline : responseDeadline;
        Long slaMinutesRemaining = activeDeadline == null ? null : Duration.between(now, activeDeadline).toMinutes();
        return new AdminQueueCaseItemResponse(
                queueKey.getKey(),
                resolveCaseType(queueKey).name(),
                row.caseId(),
                row.title(),
                row.subtitle(),
                row.status(),
                resolveSeverity(queueKey),
                createdAt,
                row.updatedAt(),
                ageMinutes,
                row.assignedAdminUserId(),
                row.assignedAdminDisplayName(),
                row.assignedAt(),
                buildDetailPath(queueKey, row),
                availableActions(queueKey),
                row.issueType(),
                row.issueSubmittedAt(),
                responseDeadline,
                row.adminEscalatedAt(),
                adminDeadline,
                row.adminSlaOverdueAt(),
                row.adminSlaReminderCount(),
                autoReleaseAt,
                disputeSlaStatus,
                slaMinutesRemaining);
    }

    private LocalDateTime toBusinessTime(java.time.Instant instant) {
        return BookingTime.fromInstant(instant);
    }

    private Pageable buildPageable(AdminQueueCaseListRequest request) {
        int page = Math.max(request.getPage(), 0);
        int size = Math.min(Math.max(request.getSize(), 1), 100);
        String sortBy = resolveSortBy(request.getSortBy());
        Sort.Direction direction = resolveDirection(request.getDirection(), sortBy);
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }

    private String resolveSortBy(String sortBy) {
        if (sortBy == null || !ALLOWED_SORT_FIELDS.contains(sortBy)) {
            return "createdAt";
        }
        return sortBy;
    }

    private Sort.Direction resolveDirection(String direction, String sortBy) {
        if (direction == null || direction.isBlank()) {
            return "createdAt".equals(sortBy) ? Sort.Direction.ASC : Sort.Direction.DESC;
        }
        try {
            return Sort.Direction.valueOf(direction.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return "createdAt".equals(sortBy) ? Sort.Direction.ASC : Sort.Direction.DESC;
        }
    }

    private AdminCaseType resolveCaseType(AdminQueueKey queueKey) {
        if (adminQueueDescriptorRegistry != null) {
            return adminQueueDescriptorRegistry.resolveCaseType(queueKey);
        }
        return null;
    }

    private String resolveSeverity(AdminQueueKey queueKey) {
        if (adminQueueDescriptorRegistry != null) {
            return adminQueueDescriptorRegistry.resolveSeverity(queueKey);
        }
        return "medium";
    }

    private String buildDetailPath(AdminQueueKey queueKey, AdminQueueQueryRepository.QueueCaseRow row) {
        if (adminQueueDescriptorRegistry != null) {
            return adminQueueDescriptorRegistry.buildDetailPath(queueKey,
                    row != null && row.detailRefId() != null ? String.valueOf(row.detailRefId()) : "");
        }
        return "";
    }

    private List<String> availableActions(AdminQueueKey queueKey) {
        if (adminQueueDescriptorRegistry != null) {
            return adminQueueDescriptorRegistry.availableActions(queueKey);
        }
        return List.of("VIEW_DETAIL", "ASSIGN_TO_ME");
    }
}
