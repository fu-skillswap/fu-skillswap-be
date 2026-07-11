package com.fptu.exe.skillswap.modules.admin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.modules.admin.domain.AdminCaseActivityEventType;
import com.fptu.exe.skillswap.modules.admin.domain.AdminCaseAssignment;
import com.fptu.exe.skillswap.modules.admin.domain.AdminCaseType;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCaseActivityListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminCaseActivityItemResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminCaseOwnershipResponse;
import com.fptu.exe.skillswap.modules.admin.repository.AdminCaseAssignmentRepository;
import com.fptu.exe.skillswap.modules.admin.repository.AdminNoteRepository;
import com.fptu.exe.skillswap.modules.admin.repository.AuditLogRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminCaseService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AdminCaseSupportService adminCaseSupportService;
    private final AdminCaseAssignmentRepository adminCaseAssignmentRepository;
    private final AdminNoteRepository adminNoteRepository;
    private final AuditLogRepository auditLogRepository;
    private final AdminAuditWriterService adminAuditWriterService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AdminCaseOwnershipResponse getOwnership(String rawCaseType, UUID caseId) {
        AdminCaseType caseType = AdminCaseType.parse(rawCaseType);
        adminCaseSupportService.assertCaseExists(caseType, caseId);
        return toOwnershipResponse(caseType, caseId, adminCaseAssignmentRepository.findByCaseTypeAndCaseId(caseType.name(), caseId).orElse(null));
    }

    @Transactional
    public AdminCaseOwnershipResponse assignToCurrentAdmin(UUID adminUserId, String rawCaseType, UUID caseId) {
        AdminCaseType caseType = AdminCaseType.parse(rawCaseType);
        adminCaseSupportService.assertCaseExists(caseType, caseId);
        User adminUser = adminCaseSupportService.requireAdminUser(adminUserId);

        Optional<AdminCaseAssignment> existingAssignment = adminCaseAssignmentRepository.findByCaseTypeAndCaseId(caseType.name(), caseId);
        if (existingAssignment.isPresent()) {
            AdminCaseAssignment assignment = existingAssignment.get();
            if (assignment.getAssignedAdminUser().getId().equals(adminUserId)) {
                return toOwnershipResponse(caseType, caseId, assignment);
            }
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Case này đang được admin khác xử lý");
        }

        try {
            AdminCaseAssignment assignment = adminCaseAssignmentRepository.save(AdminCaseAssignment.builder()
                    .caseType(caseType.name())
                    .caseId(caseId)
                    .assignedAdminUser(adminUser)
                    .build());
            adminAuditWriterService.writeOperatorEvent(
                    adminUserId,
                    caseType.name(),
                    caseId,
                    AdminCaseActivityEventType.CASE_ASSIGNMENT.name(),
                    null,
                    Map.of(
                            "assignedAdminUserId", adminUserId,
                            "assignedAdminDisplayName", adminUser.getFullName()
                    )
            );
            return toOwnershipResponse(caseType, caseId, assignment);
        } catch (DataIntegrityViolationException ex) {
            AdminCaseAssignment assignment = adminCaseAssignmentRepository.findByCaseTypeAndCaseId(caseType.name(), caseId)
                    .orElseThrow(() -> ex);
            if (assignment.getAssignedAdminUser().getId().equals(adminUserId)) {
                return toOwnershipResponse(caseType, caseId, assignment);
            }
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Case này đang được admin khác xử lý");
        }
    }

    @Transactional
    public AdminCaseOwnershipResponse unassign(UUID adminUserId, Set<RoleCode> roles, String rawCaseType, UUID caseId) {
        AdminCaseType caseType = AdminCaseType.parse(rawCaseType);
        adminCaseSupportService.assertCaseExists(caseType, caseId);
        adminCaseSupportService.requireAdminUser(adminUserId);

        AdminCaseAssignment assignment = adminCaseAssignmentRepository.findByCaseTypeAndCaseId(caseType.name(), caseId).orElse(null);
        if (assignment == null) {
            return toOwnershipResponse(caseType, caseId, null);
        }

        boolean isOwner = assignment.getAssignedAdminUser().getId().equals(adminUserId);
        boolean isSystemAdmin = roles != null && roles.contains(RoleCode.SYSTEM_ADMIN);
        if (!isOwner && !isSystemAdmin) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền gỡ ownership của case này");
        }

        Map<String, Object> oldValue = Map.of(
                "assignedAdminUserId", assignment.getAssignedAdminUser().getId(),
                "assignedAdminDisplayName", assignment.getAssignedAdminUser().getFullName(),
                "assignedAt", assignment.getAssignedAt()
        );
        adminCaseAssignmentRepository.delete(assignment);
        adminAuditWriterService.writeOperatorEvent(
                adminUserId,
                caseType.name(),
                caseId,
                AdminCaseActivityEventType.CASE_UNASSIGNMENT.name(),
                oldValue,
                new LinkedHashMap<>()
        );
        return toOwnershipResponse(caseType, caseId, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminCaseActivityItemResponse> getActivity(String rawCaseType, UUID caseId, AdminCaseActivityListRequest request) {
        AdminCaseType caseType = AdminCaseType.parse(rawCaseType);
        adminCaseSupportService.assertCaseExists(caseType, caseId);

        List<AdminCaseActivityItemResponse> items = new ArrayList<>();
        adminNoteRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(caseType.name(), caseId)
                .forEach(note -> items.add(new AdminCaseActivityItemResponse(
                        AdminCaseActivityEventType.ADMIN_NOTE.name(),
                        note.getCreatedAt(),
                        note.getAdminUser().getId(),
                        note.getAdminUser().getFullName(),
                        "Ghi chú nội bộ",
                        note.getNote(),
                        "ADMIN_NOTE"
                )));

        auditLogRepository.findByEntityTypeIgnoreCaseAndEntityIdOrderByCreatedAtDesc(caseType.name(), caseId)
                .forEach(log -> items.add(mapAuditLogToActivity(log)));

        items.sort(Comparator.comparing(AdminCaseActivityItemResponse::occurredAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(AdminCaseActivityItemResponse::eventType));

        Pageable pageable = PageRequest.of(
                Math.max(request == null ? 0 : request.getPage(), 0),
                Math.min(Math.max(request == null ? 10 : request.getSize(), 1), 100)
        );
        int fromIndex = Math.min((int) pageable.getOffset(), items.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), items.size());
        List<AdminCaseActivityItemResponse> pageContent = items.subList(fromIndex, toIndex);

        return PageResponse.<AdminCaseActivityItemResponse>builder()
                .content(pageContent)
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .totalElements(items.size())
                .totalPages(items.isEmpty() ? 0 : (int) Math.ceil((double) items.size() / pageable.getPageSize()))
                .last(toIndex >= items.size())
                .build();
    }

    private AdminCaseActivityItemResponse mapAuditLogToActivity(com.fptu.exe.skillswap.modules.admin.domain.AuditLog auditLog) {
        Map<String, Object> newValue = parseJsonMap(auditLog.getNewValue());
        String operatorEventType = newValue == null ? null : (String) newValue.get("operatorEventType");
        AdminCaseActivityEventType eventType = parseOperatorEventType(operatorEventType);
        String title = switch (eventType) {
            case CASE_ASSIGNMENT -> "Nhận xử lý case";
            case CASE_UNASSIGNMENT -> "Gỡ ownership";
            case EMAIL_RETRY_REQUESTED -> "Yêu cầu gửi lại email";
            case VERIFICATION_LOCK_RELEASED -> "Giải phóng review lock";
            default -> "Audit nội bộ";
        };
        String description = switch (eventType) {
            case CASE_ASSIGNMENT -> "Admin đã nhận ownership của case này.";
            case CASE_UNASSIGNMENT -> "Ownership của case đã được gỡ khỏi admin hiện tại.";
            case EMAIL_RETRY_REQUESTED -> "Email outbox failed đã được đưa về trạng thái chờ gửi lại.";
            case VERIFICATION_LOCK_RELEASED -> "Review lock của mentor verification request đã được giải phóng.";
            default -> "Có thay đổi vận hành nội bộ trên case này.";
        };

        return new AdminCaseActivityItemResponse(
                eventType.name(),
                auditLog.getCreatedAt(),
                auditLog.getActor() == null ? null : auditLog.getActor().getId(),
                auditLog.getActor() == null ? null : auditLog.getActor().getFullName(),
                title,
                description,
                "AUDIT_LOG"
        );
    }

    private AdminCaseActivityEventType parseOperatorEventType(String operatorEventType) {
        if (operatorEventType == null || operatorEventType.isBlank()) {
            return AdminCaseActivityEventType.AUDIT_LOG;
        }
        try {
            return AdminCaseActivityEventType.valueOf(operatorEventType);
        } catch (IllegalArgumentException ex) {
            return AdminCaseActivityEventType.AUDIT_LOG;
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            return null;
        }
    }

    private AdminCaseOwnershipResponse toOwnershipResponse(AdminCaseType caseType, UUID caseId, AdminCaseAssignment assignment) {
        return new AdminCaseOwnershipResponse(
                caseType.name(),
                caseId,
                assignment != null,
                assignment == null ? null : assignment.getAssignedAdminUser().getId(),
                assignment == null ? null : assignment.getAssignedAdminUser().getFullName(),
                assignment == null ? null : assignment.getAssignedAt()
        );
    }
}
