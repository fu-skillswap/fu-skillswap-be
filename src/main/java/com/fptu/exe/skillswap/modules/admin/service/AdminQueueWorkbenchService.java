package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.domain.AdminCaseType;
import com.fptu.exe.skillswap.modules.admin.domain.AdminQueueKey;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminQueueCaseListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminQueueCaseItemResponse;
import com.fptu.exe.skillswap.modules.admin.repository.AdminQueueQueryRepository;
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

    public PageResponse<AdminQueueCaseItemResponse> getQueueItems(UUID adminUserId, AdminQueueCaseListRequest request) {
        AdminQueueCaseListRequest safeRequest = request == null ? new AdminQueueCaseListRequest() : request;
        if (Boolean.TRUE.equals(safeRequest.getAssignedToMe()) && Boolean.TRUE.equals(safeRequest.getUnassignedOnly())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "assignedToMe và unassignedOnly không thể cùng bật");
        }

        AdminQueueKey queueKey = AdminQueueKey.parse(safeRequest.getQueueKey());
        Pageable pageable = buildPageable(safeRequest);
        PageImpl<AdminQueueQueryRepository.QueueCaseRow> page = adminQueueQueryRepository.findQueueItems(
                queueKey,
                adminUserId,
                safeRequest.getAssignedToMe(),
                safeRequest.getUnassignedOnly(),
                pageable
        );

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
        long ageMinutes = createdAt == null ? 0L : Math.max(0L, Duration.between(createdAt, DateTimeUtil.now()).toMinutes());
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
                availableActions(queueKey)
        );
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
        return switch (queueKey) {
            case MENTOR_VERIFICATION_PENDING_REVIEW -> AdminCaseType.MENTOR_VERIFICATION_REQUEST;
            case BOOKING_UNDER_REVIEW, BOOKINGS_ACCEPTED_AWAITING_PAYMENT -> AdminCaseType.BOOKING;
            case FORUM_REPORTS_OPEN -> AdminCaseType.FORUM_REPORT;
            case PAYOUT_REQUESTS_REQUESTED -> AdminCaseType.PAYOUT_REQUEST;
            case PAYMENT_ORDERS_FAILED -> AdminCaseType.PAYMENT_ORDER;
            case EMAIL_OUTBOX_FAILED -> AdminCaseType.EMAIL_OUTBOX;
        };
    }

    private String resolveSeverity(AdminQueueKey queueKey) {
        return switch (queueKey) {
            case MENTOR_VERIFICATION_PENDING_REVIEW, BOOKING_UNDER_REVIEW, FORUM_REPORTS_OPEN -> "high";
            case PAYOUT_REQUESTS_REQUESTED, PAYMENT_ORDERS_FAILED, EMAIL_OUTBOX_FAILED -> "medium";
            case BOOKINGS_ACCEPTED_AWAITING_PAYMENT -> "low";
        };
    }

    private String buildDetailPath(AdminQueueKey queueKey, AdminQueueQueryRepository.QueueCaseRow row) {
        return switch (queueKey) {
            case MENTOR_VERIFICATION_PENDING_REVIEW -> "/api/admin/mentor-verification/requests/" + row.detailRefId();
            case BOOKING_UNDER_REVIEW, BOOKINGS_ACCEPTED_AWAITING_PAYMENT -> "/api/admin/bookings/" + row.detailRefId();
            case FORUM_REPORTS_OPEN -> "/api/admin/forum/reports/" + row.detailRefId();
            case PAYOUT_REQUESTS_REQUESTED -> "/api/admin/payout-requests/" + row.detailRefId();
            case PAYMENT_ORDERS_FAILED -> "/api/admin/bookings/" + row.detailRefId();
            case EMAIL_OUTBOX_FAILED -> "/api/admin/email-outbox/" + row.detailRefId();
        };
    }

    private List<String> availableActions(AdminQueueKey queueKey) {
        return switch (queueKey) {
            case EMAIL_OUTBOX_FAILED -> List.of("VIEW_DETAIL", "ASSIGN_TO_ME", "RETRY_EMAIL");
            default -> List.of("VIEW_DETAIL", "ASSIGN_TO_ME");
        };
    }
}
