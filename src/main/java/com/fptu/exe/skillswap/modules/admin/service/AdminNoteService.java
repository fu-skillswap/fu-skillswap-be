package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.domain.AdminNote;
import com.fptu.exe.skillswap.modules.admin.domain.AdminNoteTargetType;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminNoteCreateRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminNoteListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminNoteResponse;
import com.fptu.exe.skillswap.modules.admin.repository.AdminNoteRepository;
import com.fptu.exe.skillswap.modules.booking.port.BookingQueryPort;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPort;
import com.fptu.exe.skillswap.modules.identity.port.AdminUserReference;
import com.fptu.exe.skillswap.modules.identity.port.UserAdminPort;
import com.fptu.exe.skillswap.modules.mentor.port.MentorAdminPort;
import com.fptu.exe.skillswap.modules.mentor.port.MentorVerificationAdminPort;
import com.fptu.exe.skillswap.modules.notification.port.EmailOutboxAdminPort;
import com.fptu.exe.skillswap.modules.payment.port.PaymentAdminPort;
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
public class AdminNoteService {

    private static final List<String> ALLOWED_SORT_FIELDS = List.of("createdAt", "targetType");

    private final AdminNoteRepository adminNoteRepository;
    private final UserAdminPort userAdminPort;
    private final MentorAdminPort mentorAdminPort;
    private final MentorVerificationAdminPort mentorVerificationAdminPort;
    private final BookingQueryPort bookingQueryPort;
    private final ForumAdminPort forumAdminPort;
    private final PaymentAdminPort paymentAdminPort;
    private final EmailOutboxAdminPort emailOutboxAdminPort;

    @Transactional(readOnly = true)
    public PageResponse<AdminNoteResponse> getNotes(AdminNoteListRequest request) {
        AdminNoteListRequest safeRequest = request == null ? new AdminNoteListRequest() : request;
        Page<AdminNote> page = adminNoteRepository.searchForAdmin(
                normalizeBlankToNull(safeRequest.getTargetType()),
                safeRequest.getTargetId(),
                buildPageable(safeRequest)
        );

        return PageResponse.<AdminNoteResponse>builder()
                .content(page.getContent().stream().map(this::toResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Transactional
    public AdminNoteResponse createNote(UUID adminUserId, AdminNoteCreateRequest request) {
        if (adminUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }

        AdminUserReference adminUser = userAdminPort.requireAdminReference(adminUserId);

        AdminNoteTargetType targetType = parseTargetType(request.getTargetType());
        validateTargetExists(targetType, request.getTargetId());

        AdminNote note = adminNoteRepository.save(AdminNote.builder()
                .targetType(targetType.name())
                .targetId(request.getTargetId())
                .adminUserId(adminUser.userId())
                .note(request.getNote().trim())
                .build());

        return toResponse(note);
    }

    private void validateTargetExists(AdminNoteTargetType targetType, UUID targetId) {
        if (targetId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "targetId không hợp lệ");
        }

        boolean exists = switch (targetType) {
            case USER -> userAdminPort.findVisibleUserSummary(targetId).isPresent();
            case MENTOR -> mentorAdminPort.existsById(targetId);
            case MENTOR_VERIFICATION_REQUEST -> mentorVerificationAdminPort.existsById(targetId);
            case BOOKING -> bookingQueryPort.existsById(targetId);
            case FORUM_REPORT -> forumAdminPort.existsReportById(targetId);
            case PAYOUT_REQUEST -> paymentAdminPort.existsPayoutRequestById(targetId);
            case PAYMENT_ORDER -> paymentAdminPort.existsPaymentOrderById(targetId);
            case EMAIL_OUTBOX -> emailOutboxAdminPort.existsById(targetId);
        };

        if (!exists) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy target để ghi chú");
        }
    }

    private AdminNoteResponse toResponse(AdminNote note) {
        return new AdminNoteResponse(
                note.getId(),
                note.getTargetType(),
                note.getTargetId(),
                note.getNote(),
                note.getAdminUserId(),
                userAdminPort.findReference(note.getAdminUserId()).map(AdminUserReference::displayName).orElse(null),
                note.getCreatedAt()
        );
    }

    private Pageable buildPageable(AdminNoteListRequest request) {
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

    private AdminNoteTargetType parseTargetType(String targetType) {
        if (targetType == null || targetType.isBlank()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "targetType không hợp lệ");
        }
        try {
            return AdminNoteTargetType.valueOf(targetType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "targetType không hợp lệ");
        }
    }

    private String normalizeBlankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
