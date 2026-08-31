package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.mentor.port.MentorViolationCommandPort;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorViolationEvent;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorViolationType;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorViolationSeverity;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorViolationSource;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorViolationHistoryResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorViolationItemResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorViolationEventRepository;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MentorViolationService implements MentorViolationCommandPort {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int ACTIVE_SCORE_WINDOW_DAYS = 90;

    private final MentorViolationEventRepository violationRepository;
    private final MentorProfileRepository mentorProfileRepository;

    @Transactional
    public void record(UUID mentorUserId, UUID bookingId, MentorViolationType type, String reason) {
        record(mentorUserId, bookingId, MentorViolationSource.BOOKING, bookingId, type,
                MentorViolationPolicy.severityFor(type), null, reason, null);
    }

    @Transactional
    public void record(UUID mentorUserId, UUID bookingId, MentorViolationSource sourceModule, UUID sourceReferenceId,
                       MentorViolationType type, MentorViolationSeverity severity, UUID decisionByUserId,
                       String reason, String decisionNote) {
        if (mentorUserId == null || type == null) return;
        MentorViolationSource source = sourceModule == null ? MentorViolationSource.ADMIN : sourceModule;
        UUID referenceId = sourceReferenceId == null ? (bookingId == null ? mentorUserId : bookingId) : sourceReferenceId;
        String operationKey = source.name() + ":" + type.name() + ":" + referenceId;
        if (violationRepository.existsByOperationKey(operationKey)) return;
        violationRepository.save(MentorViolationEvent.builder()
                .mentorUserId(mentorUserId)
                .bookingId(bookingId)
                .sourceModule(source)
                .sourceReferenceId(sourceReferenceId)
                .violationType(type)
                .severity(severity == null ? MentorViolationPolicy.severityFor(type) : severity)
                .points(pointsFor(type, severity))
                .reason(normalizeReason(reason, type))
                .decisionByUserId(decisionByUserId)
                .decisionNote(trim(decisionNote, 1000))
                .operationKey(operationKey)
                .occurredAt(DateTimeUtil.now())
                .build());
        applyBookingRestriction(mentorUserId);
    }

    @Transactional
    public void recordAdminConfirmed(UUID mentorUserId, MentorViolationSource sourceModule, UUID sourceReferenceId,
                                     MentorViolationType type, MentorViolationSeverity severity, UUID adminUserId,
                                     String reason, String decisionNote) {
        if (mentorUserId == null || !mentorProfileRepository.existsById(mentorUserId)) return;
        record(mentorUserId, null, sourceModule, sourceReferenceId, type, severity, adminUserId, reason, decisionNote);
    }

    @Override
    @Transactional
    public void recordConfirmedViolation(MentorViolationCommand command) {
        if (command == null || command.mentorUserId() == null || command.type() == null) {
            return;
        }
        try {
            recordAdminConfirmed(
                    command.mentorUserId(),
                    command.source() == null ? MentorViolationSource.ADMIN : MentorViolationSource.valueOf(command.source()),
                    command.sourceReferenceId(),
                    MentorViolationType.valueOf(command.type()),
                    command.severity() == null ? null : MentorViolationSeverity.valueOf(command.severity()),
                    command.decidedByUserId(), command.reason(), command.decisionNote());
        } catch (IllegalArgumentException ex) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Dữ liệu mentor violation không hợp lệ");
        }
    }

    @Transactional
    public void reverse(UUID mentorUserId, UUID violationId, UUID adminUserId, String reason) {
        MentorViolationEvent event = violationRepository.findByIdForUpdate(violationId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy điểm vi phạm"));
        if (!event.getMentorUserId().equals(mentorUserId)) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Điểm vi phạm không thuộc mentor này");
        }
        if (event.getReversedAt() != null) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Điểm vi phạm đã được đảo trước đó");
        }
        event.setReversedAt(DateTimeUtil.now());
        event.setReversedByUserId(adminUserId);
        event.setReversalReason(normalizeReason(reason, MentorViolationType.ADMIN_CONFIRMED_BREACH));
        violationRepository.save(event);
        recalculateBookingRestriction(mentorUserId);
    }

    @Transactional(readOnly = true)
    public MentorViolationHistoryResponse getHistory(UUID mentorUserId, int page, int size) {
        if (mentorUserId == null || !mentorProfileRepository.existsById(mentorUserId)) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ mentor");
        }
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(MAX_PAGE_SIZE, size));
        Page<MentorViolationEvent> result = violationRepository.findByMentorUserIdOrderByOccurredAtDesc(
                mentorUserId,
                PageRequest.of(safePage, safeSize)
        );
        PageResponse<MentorViolationItemResponse> history = PageResponse.<MentorViolationItemResponse>builder()
                .content(result.getContent().stream().map(this::toItem).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build();
        LocalDateTime now = DateTimeUtil.now();
        LocalDateTime windowStart = now.minusDays(ACTIVE_SCORE_WINDOW_DAYS);
        MentorProfile profile = mentorProfileRepository.findById(mentorUserId).orElseThrow(() ->
                new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ mentor"));
        return new MentorViolationHistoryResponse(
                mentorUserId,
                violationRepository.sumPointsByMentorUserId(mentorUserId),
                violationRepository.sumActivePointsByMentorUserId(mentorUserId, windowStart),
                windowStart,
                profile.getBookingSuspendedUntil(),
                violationRepository.countByMentorUserId(mentorUserId),
                history
        );
    }

    private MentorViolationItemResponse toItem(MentorViolationEvent event) {
        return new MentorViolationItemResponse(event.getId(), event.getViolationType(), event.getSourceModule(),
                event.getSeverity(), event.getPoints(), event.getReason(), event.getBookingId(),
                event.getOccurredAt(), event.getReversedAt());
    }

    private void applyBookingRestriction(UUID mentorUserId) {
        recalculateBookingRestriction(mentorUserId);
    }

    private void recalculateBookingRestriction(UUID mentorUserId) {
        mentorProfileRepository.findByIdForUpdate(mentorUserId).ifPresent(profile -> {
            BigDecimal score = violationRepository.sumActivePointsByMentorUserId(mentorUserId,
                    DateTimeUtil.now().minusDays(ACTIVE_SCORE_WINDOW_DAYS));
            int days = score.compareTo(new BigDecimal("10")) >= 0 ? 30
                    : score.compareTo(new BigDecimal("6")) >= 0 ? 14
                    : score.compareTo(new BigDecimal("3")) >= 0 ? 3 : 0;
            LocalDateTime candidate = days == 0 ? null : DateTimeUtil.now().plusDays(days);
            if (!java.util.Objects.equals(profile.getBookingSuspendedUntil(), candidate)) {
                profile.setBookingSuspendedUntil(candidate);
                mentorProfileRepository.save(profile);
            }
        });
    }

    private BigDecimal pointsFor(MentorViolationType type, MentorViolationSeverity severity) {
        return switch (type) {
            case LATE_CANCELLATION, COMPLETION_OVERDUE, MENTOR_NO_SHOW -> MentorViolationPolicy.pointsFor(type);
            default -> MentorViolationPolicy.pointsForSeverity(severity);
        };
    }

    private String trim(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String normalizeReason(String reason, MentorViolationType type) {
        String value = reason == null ? "" : reason.trim();
        if (!value.isEmpty()) return value.length() <= 500 ? value : value.substring(0, 500);
        return switch (type) {
            case LATE_CANCELLATION -> "Mentor hủy booking quá sát giờ bắt đầu.";
            case COMPLETION_OVERDUE -> "Mentor không xác nhận hoàn tất trong 24 giờ sau buổi học.";
            case MENTOR_NO_SHOW -> "Mentor được xác định không có mặt trong buổi học.";
            case BOOKING_POLICY_BREACH -> "Admin xác nhận mentor vi phạm quy tắc booking.";
            case CHAT_POLICY_BREACH -> "Admin xác nhận mentor vi phạm quy tắc chat.";
            case FORUM_POLICY_BREACH -> "Admin xác nhận mentor vi phạm quy tắc forum.";
            case VERIFICATION_FRAUD -> "Admin xác nhận minh chứng xác minh không trung thực.";
            case ADMIN_CONFIRMED_BREACH -> "Admin xác nhận mentor vi phạm chính sách hệ thống.";
        };
    }
}
