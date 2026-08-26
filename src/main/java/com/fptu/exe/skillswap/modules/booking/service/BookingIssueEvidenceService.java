package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingDisputeSlaStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueEvidence;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueEvidenceState;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueEvidenceSubmissionSide;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueEvidenceUploadIntent;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueEvidenceUploadIntentStatus;
import com.fptu.exe.skillswap.modules.booking.dto.request.BookingIssueEvidenceUploadIntentRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingIssueDetailResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingIssueEvidenceDownloadResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingIssueEvidenceResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingIssueEvidenceUploadIntentResponse;
import com.fptu.exe.skillswap.modules.booking.repository.BookingIssueEvidenceRepository;
import com.fptu.exe.skillswap.modules.booking.repository.BookingIssueEvidenceUploadIntentRepository;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Private, immutable evidence pipeline for a booking dispute. */
@Service
@RequiredArgsConstructor
public class BookingIssueEvidenceService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "application/pdf");

    private final BookingRepository bookingRepository;
    private final BookingIssueEvidenceUploadIntentRepository intentRepository;
    private final BookingIssueEvidenceRepository evidenceRepository;
    private final StorageGateway storageGateway;
    private final BookingIssueEvidenceProperties properties;
    private final TimeProvider timeProvider;

    @Transactional
    public BookingIssueEvidenceUploadIntentResponse createUploadIntent(UUID actorUserId, UUID bookingId,
                                                                        BookingIssueEvidenceUploadIntentRequest request) {
        Booking booking = requireParticipantBooking(actorUserId, bookingId);
        assertEvidenceUploadAllowed(booking, actorUserId, timeProvider.instant());
        validateFileRequest(request);

        Instant now = timeProvider.instant();
        UUID intentId = UUID.randomUUID();
        String contentType = normalizeContentType(request.contentType());
        String storageKey = "booking-disputes/staging/" + bookingId + "/" + intentId + extensionFor(contentType);
        BookingIssueEvidenceUploadIntent intent = BookingIssueEvidenceUploadIntent.builder()
                .id(intentId).booking(booking).ownerUserId(actorUserId).stagingStorageKey(storageKey)
                .originalFilename(request.filename().trim()).contentType(contentType).expectedSizeBytes(request.sizeBytes())
                .expiresAtUtc(now.plus(Duration.ofMinutes(properties.getUploadIntentTtlMinutes())))
                .createdAtUtc(now).build();
        intentRepository.save(intent);
        StorageGateway.PrivatePresignedUpload upload = storageGateway.generatePrivateUploadUrl(
                storageKey, contentType, Duration.ofMinutes(properties.getUploadIntentTtlMinutes()));
        return new BookingIssueEvidenceUploadIntentResponse(intentId, upload.uploadUrl(),
                BookingTime.toOffsetDateTime(upload.expiresAt()), contentType);
    }

    @Transactional
    public BookingIssueEvidenceResponse confirmUploadIntent(UUID actorUserId, UUID bookingId, UUID intentId) {
        Instant now = timeProvider.instant();
        BookingIssueEvidenceUploadIntent intent = intentRepository.findByIdForUpdate(intentId)
                .orElseThrow(() -> new BaseException(ErrorCode.BOOKING_ISSUE_EVIDENCE_INTENT_INVALID, "Không tìm thấy upload intent minh chứng"));
        if (!intent.getBooking().getId().equals(bookingId) || !actorUserId.equals(intent.getOwnerUserId())) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền xác nhận file minh chứng này");
        }
        assertEvidenceUploadAllowed(intent.getBooking(), actorUserId, now);
        if (intent.getStatus() == BookingIssueEvidenceUploadIntentStatus.CONFIRMED) {
            BookingIssueEvidence existing = evidenceRepository.findByUploadIntentId(intentId)
                    .orElseThrow(() -> new BaseException(ErrorCode.BOOKING_ISSUE_EVIDENCE_INTENT_INVALID, "Upload intent không hợp lệ"));
            return toResponse(existing, true);
        }
        if (intent.getStatus() != BookingIssueEvidenceUploadIntentStatus.PENDING_UPLOAD || !now.isBefore(intent.getExpiresAtUtc())) {
            intent.setStatus(BookingIssueEvidenceUploadIntentStatus.EXPIRED);
            throw new BaseException(ErrorCode.BOOKING_ISSUE_EVIDENCE_INTENT_INVALID, "Upload intent đã hết hạn, vui lòng tạo lại");
        }

        StorageGateway.ObjectMetadata metadata = storageGateway.headObject(intent.getStagingStorageKey());
        long actualSize = metadata.sizeBytes() == 0L ? intent.getExpectedSizeBytes() : metadata.sizeBytes();
        if (actualSize != intent.getExpectedSizeBytes() || actualSize > properties.getMaxFileSizeBytes()) {
            intent.setStatus(BookingIssueEvidenceUploadIntentStatus.REJECTED);
            throw new BaseException(ErrorCode.BOOKING_ISSUE_EVIDENCE_INVALID, "Kích thước file minh chứng không hợp lệ");
        }
        verifyFileSignature(intent.getStagingStorageKey(), intent.getContentType());

        UUID evidenceId = UUID.randomUUID();
        String finalKey = "booking-disputes/evidence/" + bookingId + "/" + evidenceId + extensionFor(intent.getContentType());
        storageGateway.copyPrivateObject(intent.getStagingStorageKey(), finalKey, intent.getContentType());
        intent.setStatus(BookingIssueEvidenceUploadIntentStatus.CONFIRMED);
        intent.setConfirmedAtUtc(now);
        BookingIssueEvidence evidence = evidenceRepository.save(BookingIssueEvidence.builder()
                .id(evidenceId).booking(intent.getBooking()).uploadIntent(intent).submittedByUserId(actorUserId)
                .storageKey(finalKey).originalFilename(intent.getOriginalFilename()).contentType(intent.getContentType())
                .sizeBytes(actualSize).confirmedAtUtc(now).build());
        return toResponse(evidence, true);
    }

    /** Called inside the booking issue transaction after the booking row has been locked. */
    public void attachReporterEvidence(Booking booking, UUID actorUserId, List<UUID> evidenceIds, Instant now) {
        attachEvidence(booking, actorUserId, evidenceIds, BookingIssueEvidenceSubmissionSide.REPORTER, true, now);
    }

    /** Called inside the booking issue response transaction after the booking row has been locked. */
    public void attachResponderEvidence(Booking booking, UUID actorUserId, List<UUID> evidenceIds, Instant now) {
        attachEvidence(booking, actorUserId, evidenceIds, BookingIssueEvidenceSubmissionSide.RESPONDER, false, now);
    }

    public void assertReporterReplayMatches(Booking booking, UUID actorUserId, List<UUID> evidenceIds) {
        assertAttachedSetMatches(booking, actorUserId, evidenceIds, BookingIssueEvidenceSubmissionSide.REPORTER);
    }

    public void assertResponderReplayMatches(Booking booking, UUID actorUserId, List<UUID> evidenceIds) {
        assertAttachedSetMatches(booking, actorUserId, evidenceIds, BookingIssueEvidenceSubmissionSide.RESPONDER);
    }

    @Transactional(readOnly = true)
    public BookingIssueDetailResponse getForParticipant(UUID actorUserId, UUID bookingId) {
        Booking booking = requireParticipantBooking(actorUserId, bookingId);
        return detail(booking, evidenceRepository.findByBookingIdAndStateInOrderByAttachedAtUtcAsc(bookingId,
                List.of(BookingIssueEvidenceState.ACTIVE)), false);
    }

    @Transactional(readOnly = true)
    public BookingIssueEvidenceDownloadResponse downloadForParticipant(UUID actorUserId, UUID bookingId, UUID evidenceId) {
        BookingIssueEvidence evidence = evidenceRepository.findWithBookingById(evidenceId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy file minh chứng"));
        if (!evidence.getBooking().getId().equals(bookingId)) throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy file minh chứng");
        assertParticipant(evidence.getBooking(), actorUserId);
        return download(evidence, false);
    }

    @Transactional(readOnly = true)
    public BookingIssueDetailResponse getForAdmin(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
        return detail(booking, evidenceRepository.findByBookingIdAndStateInOrderByAttachedAtUtcAsc(bookingId,
                List.of(BookingIssueEvidenceState.ACTIVE, BookingIssueEvidenceState.HIDDEN, BookingIssueEvidenceState.DELETED)), true);
    }

    @Transactional(readOnly = true)
    public BookingIssueEvidenceDownloadResponse downloadForAdmin(UUID bookingId, UUID evidenceId) {
        BookingIssueEvidence evidence = evidenceRepository.findWithBookingById(evidenceId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy file minh chứng"));
        if (!evidence.getBooking().getId().equals(bookingId)) throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy file minh chứng");
        return download(evidence, true);
    }

    @Transactional
    public BookingIssueEvidenceResponse setAdminVisibility(UUID bookingId, UUID evidenceId, UUID adminUserId,
                                                            boolean hidden, String reason) {
        BookingIssueEvidence evidence = evidenceRepository.findWithBookingById(evidenceId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy file minh chứng"));
        if (!evidence.getBooking().getId().equals(bookingId)) throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy file minh chứng");
        if (evidence.getState() == BookingIssueEvidenceState.DELETED) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "File minh chứng đã hết thời hạn lưu trữ");
        }
        if (hidden) {
            if (reason == null || reason.isBlank()) throw new BaseException(ErrorCode.BAD_REQUEST, "Cần nêu lý do ẩn minh chứng");
            evidence.setState(BookingIssueEvidenceState.HIDDEN);
            evidence.setHiddenAtUtc(timeProvider.instant()); evidence.setHiddenByUserId(adminUserId); evidence.setHiddenReason(reason.trim());
        } else {
            evidence.setState(BookingIssueEvidenceState.ACTIVE);
            evidence.setHiddenAtUtc(null); evidence.setHiddenByUserId(null); evidence.setHiddenReason(null);
        }
        return toResponse(evidence, true);
    }

    @Transactional
    public void cleanExpiredUploadIntents() {
        Instant now = timeProvider.instant();
        for (BookingIssueEvidenceUploadIntent intent : intentRepository.findTop100ByStatusInAndExpiresAtUtcBeforeOrderByExpiresAtUtcAsc(
                List.of(BookingIssueEvidenceUploadIntentStatus.PENDING_UPLOAD, BookingIssueEvidenceUploadIntentStatus.CONFIRMED), now)) {
            try {
                storageGateway.deletePrivateObject(intent.getStagingStorageKey());
                if (intent.getStatus() == BookingIssueEvidenceUploadIntentStatus.PENDING_UPLOAD) intent.setStatus(BookingIssueEvidenceUploadIntentStatus.EXPIRED);
            } catch (RuntimeException ignored) {
                // Preserve the row so a future run can retry deletion; private evidence itself is never deleted here.
            }
        }
    }

    @Transactional
    public void cleanResolvedEvidence() {
        Instant cutoff = timeProvider.instant().minus(Duration.ofDays(properties.getRetentionDays()));
        for (BookingIssueEvidence evidence : evidenceRepository.findTop100ReadyForRetentionDeletion(
                List.of(BookingIssueEvidenceState.ACTIVE, BookingIssueEvidenceState.HIDDEN), cutoff)) {
            try {
                storageGateway.deletePrivateObject(evidence.getStorageKey());
                evidence.setState(BookingIssueEvidenceState.DELETED);
                evidence.setDeletedAtUtc(timeProvider.instant());
            } catch (RuntimeException ignored) {
                // Preserve audit metadata and retry physical deletion later.
            }
        }
    }

    private void attachEvidence(Booking booking, UUID actorUserId, List<UUID> evidenceIds,
                                BookingIssueEvidenceSubmissionSide side, boolean required, Instant now) {
        List<UUID> ids = normalizeEvidenceIds(evidenceIds, required);
        List<BookingIssueEvidence> evidences = evidenceRepository.findAllByIdInForUpdate(ids);
        if (evidences.size() != ids.size()) throw new BaseException(ErrorCode.BOOKING_ISSUE_EVIDENCE_INVALID, "Có file minh chứng không tồn tại hoặc chưa được xác nhận");
        for (BookingIssueEvidence evidence : evidences) {
            if (!booking.getId().equals(evidence.getBooking().getId()) || !actorUserId.equals(evidence.getSubmittedByUserId())
                    || evidence.getState() != BookingIssueEvidenceState.PENDING_ATTACH || evidence.getSubmissionSide() != null) {
                throw new BaseException(ErrorCode.BOOKING_ISSUE_EVIDENCE_INVALID, "File minh chứng không hợp lệ cho issue này");
            }
            evidence.setSubmissionSide(side); evidence.setState(BookingIssueEvidenceState.ACTIVE); evidence.setAttachedAtUtc(now);
        }
    }

    private void assertAttachedSetMatches(Booking booking, UUID actorUserId, List<UUID> evidenceIds,
                                          BookingIssueEvidenceSubmissionSide side) {
        Set<UUID> expected = new HashSet<>(normalizeEvidenceIds(evidenceIds, side == BookingIssueEvidenceSubmissionSide.REPORTER));
        Set<UUID> actual = evidenceRepository.findByBookingIdAndStateInOrderByAttachedAtUtcAsc(booking.getId(), List.of(BookingIssueEvidenceState.ACTIVE))
                .stream().filter(item -> actorUserId.equals(item.getSubmittedByUserId()) && side == item.getSubmissionSide())
                .map(BookingIssueEvidence::getId).collect(java.util.stream.Collectors.toSet());
        if (!actual.equals(expected)) throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Nội dung retry không khớp với issue đã gửi");
    }

    private BookingIssueEvidenceDownloadResponse download(BookingIssueEvidence evidence, boolean admin) {
        if (evidence.getState() == BookingIssueEvidenceState.DELETED) throw new BaseException(ErrorCode.BOOKING_ISSUE_EVIDENCE_HIDDEN, "File minh chứng đã hết thời hạn lưu trữ");
        if (!admin && evidence.getState() != BookingIssueEvidenceState.ACTIVE) throw new BaseException(ErrorCode.BOOKING_ISSUE_EVIDENCE_HIDDEN, "File minh chứng hiện không khả dụng");
        StorageGateway.PrivatePresignedDownload url = storageGateway.generatePrivateDownloadUrl(evidence.getStorageKey(),
                Duration.ofMinutes(properties.getDownloadUrlTtlMinutes()), "attachment; filename=\"" + safeFilename(evidence.getOriginalFilename()) + "\"");
        return new BookingIssueEvidenceDownloadResponse(url.downloadUrl(), BookingTime.toOffsetDateTime(url.expiresAt()));
    }

    private BookingIssueDetailResponse detail(Booking booking, List<BookingIssueEvidence> evidences, boolean admin) {
        if (booking.getIssueSubmittedAtUtc() == null && booking.getIssueSubmittedAt() == null) throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking chưa có dispute");
        Instant submittedUtc = booking.getIssueSubmittedAtUtc() != null ? booking.getIssueSubmittedAtUtc() : BookingTime.toInstant(booking.getIssueSubmittedAt());
        Instant respondedUtc = booking.getIssueRespondedAtUtc() != null ? booking.getIssueRespondedAtUtc() : BookingTime.toInstant(booking.getIssueRespondedAt());
        Instant resolvedUtc = booking.getIssueResolvedAtUtc() != null ? booking.getIssueResolvedAtUtc() : BookingTime.toInstant(booking.getIssueResolvedAt());
        Instant escalatedUtc = booking.getIssueHumanReviewEscalatedAtUtc();
        Instant overdueUtc = booking.getAdminSlaOverdueAtUtc();
        BookingDisputeSlaStatus slaStatus = BookingDeadlinePolicy.resolveDisputeSlaStatus(
                submittedUtc, escalatedUtc, overdueUtc, resolvedUtc
        );
        return new BookingIssueDetailResponse(booking.getId(), booking.getStatus(), booking.getIssueType(), booking.getIssueDescription(),
                BookingTime.toOffsetDateTime(submittedUtc),
                BookingTime.toOffsetDateTime(BookingDeadlinePolicy.resolveIssueResponseDeadlineUtc(submittedUtc)),
                BookingTime.toOffsetDateTime(respondedUtc), booking.getIssueResponseNote(), BookingTime.toOffsetDateTime(resolvedUtc),
                BookingTime.toOffsetDateTime(escalatedUtc),
                BookingTime.toOffsetDateTime(BookingDeadlinePolicy.resolveAdminDisputeSlaDeadlineUtc(escalatedUtc)),
                BookingTime.toOffsetDateTime(overdueUtc), booking.getAdminSlaReminderCount(),
                BookingTime.toOffsetDateTime(BookingDeadlinePolicy.resolveAdminDisputeAutoReleaseDeadlineUtc(overdueUtc)),
                slaStatus, booking.getIssueResolutionNote(),
                evidences.stream().map(item -> toResponse(item, admin || item.getState() == BookingIssueEvidenceState.ACTIVE)).toList());
    }

    private BookingIssueEvidenceResponse toResponse(BookingIssueEvidence evidence, boolean canDownload) {
        return new BookingIssueEvidenceResponse(evidence.getId(), evidence.getOriginalFilename(), evidence.getContentType(), evidence.getSizeBytes(),
                evidence.getSubmissionSide(), evidence.getState(), BookingTime.toOffsetDateTime(evidence.getAttachedAtUtc()), canDownload);
    }

    private Booking requireParticipantBooking(UUID actorUserId, UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
        assertParticipant(booking, actorUserId);
        return booking;
    }

    private void assertParticipant(Booking booking, UUID actorUserId) {
        boolean mentee = booking.getMentee() != null && actorUserId.equals(booking.getMentee().getId());
        boolean mentor = booking.getMentorProfile() != null && actorUserId.equals(booking.getMentorProfile().getUserId());
        if (!mentee && !mentor) throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền truy cập minh chứng của booking này");
    }

    private void assertEvidenceUploadAllowed(Booking booking, UUID actorUserId, Instant now) {
        if (booking.getIssueSubmittedAtUtc() == null && booking.getIssueSubmittedAt() == null) {
            Instant end = BookingTime.resolveSelectedEndUtc(booking);
            if (end == null || now.isBefore(end) || !now.isBefore(end.plus(Duration.ofHours(PostSessionPolicy.MENTEE_REVIEW_WINDOW_HOURS)))) {
                throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Hiện chưa trong thời gian được gửi minh chứng dispute");
            }
            return;
        }
        Instant submittedAt = booking.getIssueSubmittedAtUtc() != null ? booking.getIssueSubmittedAtUtc() : BookingTime.toInstant(booking.getIssueSubmittedAt());
        if (!actorUserId.equals(booking.getIssueSubmittedByUserId()) && booking.getIssueRespondedAtUtc() == null && booking.getIssueRespondedAt() == null
                && submittedAt != null && now.isBefore(BookingDeadlinePolicy.resolveIssueResponseDeadlineUtc(submittedAt))) return;
        throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Không còn quyền gửi thêm minh chứng cho dispute này");
    }

    private void validateFileRequest(BookingIssueEvidenceUploadIntentRequest request) {
        if (request == null || request.sizeBytes() == null || request.sizeBytes() <= 0 || request.sizeBytes() > properties.getMaxFileSizeBytes()
                || request.filename() == null || request.filename().isBlank() || request.filename().contains("/") || request.filename().contains("\\")
                || !ALLOWED_CONTENT_TYPES.contains(normalizeContentType(request.contentType())) || !matchesExtension(request.filename(), normalizeContentType(request.contentType()))) {
            throw new BaseException(ErrorCode.BOOKING_ISSUE_EVIDENCE_INVALID, "File minh chứng chỉ nhận JPG, PNG hoặc PDF và tối đa 10 MB");
        }
    }

    private List<UUID> normalizeEvidenceIds(List<UUID> values, boolean required) {
        List<UUID> ids = values == null ? List.of() : values.stream().filter(java.util.Objects::nonNull).sorted(Comparator.naturalOrder()).toList();
        if ((required && ids.isEmpty()) || ids.size() > properties.getMaxFilesPerAction() || new HashSet<>(ids).size() != ids.size())
            throw new BaseException(ErrorCode.BOOKING_ISSUE_EVIDENCE_INVALID, required ? "Issue cần từ 1 đến 5 file minh chứng hợp lệ" : "Tối đa 5 file minh chứng cho phản hồi");
        return ids;
    }

    private void verifyFileSignature(String key, String contentType) {
        try (InputStream stream = storageGateway.openObject(key)) {
            byte[] bytes = stream.readNBytes(8);
            boolean valid = switch (contentType) {
                case "image/jpeg" -> bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
                case "image/png" -> bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47 && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A;
                case "application/pdf" -> bytes.length >= 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
                default -> false;
            };
            if (!valid) throw new BaseException(ErrorCode.BOOKING_ISSUE_EVIDENCE_INVALID, "Nội dung file không khớp định dạng đã khai báo");
        } catch (IOException ex) {
            throw new BaseException(ErrorCode.BOOKING_ISSUE_EVIDENCE_INVALID, "Không thể đọc file minh chứng đã upload");
        }
    }

    private String normalizeContentType(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private boolean matchesExtension(String filename, String contentType) {
        String name = filename.toLowerCase(Locale.ROOT);
        return ("image/jpeg".equals(contentType) && (name.endsWith(".jpg") || name.endsWith(".jpeg"))) || ("image/png".equals(contentType) && name.endsWith(".png")) || ("application/pdf".equals(contentType) && name.endsWith(".pdf"));
    }
    private String extensionFor(String contentType) { return "image/jpeg".equals(contentType) ? ".jpg" : "image/png".equals(contentType) ? ".png" : ".pdf"; }
    private String safeFilename(String value) { return value == null ? "evidence" : value.replace("\"", "_").replace("\r", "_").replace("\n", "_"); }
}
