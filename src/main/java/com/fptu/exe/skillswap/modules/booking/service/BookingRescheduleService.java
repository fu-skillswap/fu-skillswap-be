package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.admin.domain.AuditAction;
import com.fptu.exe.skillswap.modules.admin.domain.AuditLog;
import com.fptu.exe.skillswap.modules.admin.repository.AuditLogRepository;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingRescheduleActorRole;
import com.fptu.exe.skillswap.modules.booking.domain.BookingRescheduleRequest;
import com.fptu.exe.skillswap.modules.booking.domain.BookingRescheduleStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateBookingRescheduleRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.RespondBookingRescheduleRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingRescheduleRequestResponse;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRescheduleRequestRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import com.fptu.exe.skillswap.shared.util.AuditLogJsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingRescheduleService {

    private static final long RESCHEDULE_DEADLINE_MINUTES = 6 * 60;
    private static final long RESCHEDULE_RESPONSE_DEADLINE_MINUTES = 2 * 60;
    private static final int MAX_RESCHEDULE_COUNT = 1;

    private final BookingRepository bookingRepository;
    private final BookingRescheduleRequestRepository bookingRescheduleRequestRepository;
    private final MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;
    private final BookingSlotValidator bookingSlotValidator;
    private final NotificationService notificationService;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public BookingRescheduleRequestResponse createByMentee(UUID currentUserId, UUID bookingId, CreateBookingRescheduleRequest request) {
        return create(currentUserId, bookingId, request, BookingRescheduleActorRole.MENTEE);
    }

    @Transactional
    public BookingRescheduleRequestResponse createByMentor(UUID currentUserId, UUID bookingId, CreateBookingRescheduleRequest request) {
        return create(currentUserId, bookingId, request, BookingRescheduleActorRole.MENTOR);
    }

    @Transactional(readOnly = true)
    public List<BookingRescheduleRequestResponse> getMyBookingRequests(UUID currentUserId, UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
        assertParticipantAccess(booking, currentUserId);
        return bookingRescheduleRequestRepository.findByBookingIdOrderByCreatedAtDesc(bookingId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingRescheduleRequestResponse> getAdminBookingRequests(UUID bookingId) {
        return bookingRescheduleRequestRepository.findByBookingIdOrderByCreatedAtDesc(bookingId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public BookingRescheduleRequestResponse acceptByParticipant(UUID currentUserId, UUID requestId, RespondBookingRescheduleRequest request) {
        BookingRescheduleRequest rescheduleRequest = loadPendingRequestForResponse(requestId);
        Booking booking = rescheduleRequest.getBooking();
        BookingRescheduleActorRole responderRole = resolveParticipantResponderRole(booking, currentUserId);
        if (responderRole == rescheduleRequest.getRequesterRole()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Bạn không thể tự chấp nhận reschedule request do chính mình tạo");
        }
        return acceptInternal(rescheduleRequest, currentUserId, responderRole, request == null ? null : request.reason(), false);
    }

    @Transactional
    public BookingRescheduleRequestResponse rejectByParticipant(UUID currentUserId, UUID requestId, RespondBookingRescheduleRequest request) {
        BookingRescheduleRequest rescheduleRequest = loadPendingRequestForResponse(requestId);
        Booking booking = rescheduleRequest.getBooking();
        BookingRescheduleActorRole responderRole = resolveParticipantResponderRole(booking, currentUserId);
        if (responderRole == rescheduleRequest.getRequesterRole()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Bạn không thể tự từ chối reschedule request do chính mình tạo");
        }
        return rejectInternal(rescheduleRequest, currentUserId, responderRole, request == null ? null : request.reason(), false);
    }

    @Transactional
    public BookingRescheduleRequestResponse acceptByAdmin(UUID currentUserId, UUID requestId, RespondBookingRescheduleRequest request) {
        BookingRescheduleRequest rescheduleRequest = loadPendingRequestForResponse(requestId);
        return acceptInternal(rescheduleRequest, currentUserId, BookingRescheduleActorRole.ADMIN, request == null ? null : request.reason(), true);
    }

    @Transactional
    public BookingRescheduleRequestResponse rejectByAdmin(UUID currentUserId, UUID requestId, RespondBookingRescheduleRequest request) {
        BookingRescheduleRequest rescheduleRequest = loadPendingRequestForResponse(requestId);
        return rejectInternal(rescheduleRequest, currentUserId, BookingRescheduleActorRole.ADMIN, request == null ? null : request.reason(), true);
    }

    @Transactional
    public int expirePendingRequests() {
        LocalDateTime now = DateTimeUtil.now();
        int expired = 0;
        for (BookingRescheduleRequest request : bookingRescheduleRequestRepository.findExpirablePendingRequests(
                BookingRescheduleStatus.PENDING,
                now.plusHours(2)
        )) {
            if (request.getStatus() != BookingRescheduleStatus.PENDING) {
                continue;
            }
            if (isPastResponseDeadline(request.getBooking(), now)) {
                request.setStatus(BookingRescheduleStatus.EXPIRED);
                request.setExpiredAt(now);
                request.setRespondedAt(now);
                request.setResponseNote("Reschedule request đã hết hạn do đã qua mốc 2 giờ trước giờ học cũ.");
                bookingRescheduleRequestRepository.save(request);
                notifyExpire(request);
                expired++;
            }
        }
        return expired;
    }

    private BookingRescheduleRequestResponse create(UUID currentUserId,
                                                    UUID bookingId,
                                                    CreateBookingRescheduleRequest request,
                                                    BookingRescheduleActorRole actorRole) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu dữ liệu reschedule request");
        }
        Booking booking = bookingRepository.findByIdForSessionUpdate(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
        validateCreatePermission(booking, currentUserId, actorRole);
        validateRescheduleableBooking(booking);
        ensureWithinRescheduleWindow(booking);
        if (bookingRescheduleRequestRepository.existsByBookingIdAndStatus(bookingId, BookingRescheduleStatus.PENDING)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking hiện đã có một reschedule request đang chờ phản hồi");
        }
        MentorAvailabilitySlot currentSlot = booking.getSlot();
        if (currentSlot == null || currentSlot.getId() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Booking hiện không gắn với availability slot hợp lệ");
        }
        MentorAvailabilitySlot proposedSlot = mentorAvailabilitySlotRepository.findById(request.proposedSlotId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy slot mới được đề xuất"));
        validateProposedSlotForBooking(booking, proposedSlot);
        bookingSlotValidator.validateSelectedRange(
                proposedSlot,
                booking.getService(),
                request.proposedSelectedStartTime(),
                request.proposedSelectedEndTime(),
                DateTimeUtil.now()
        );
        bookingSlotValidator.validateServiceAttachedToSlot(proposedSlot.getId(), booking.getService().getId());

        if (sameSegment(booking, proposedSlot, request.proposedSelectedStartTime(), request.proposedSelectedEndTime())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Lịch mới phải khác lịch hiện tại của booking");
        }

        BookingRescheduleRequest entity = bookingRescheduleRequestRepository.save(BookingRescheduleRequest.builder()
                .booking(booking)
                .currentSlot(currentSlot)
                .proposedSlot(proposedSlot)
                .requestedByUserId(currentUserId)
                .requesterRole(actorRole)
                .status(BookingRescheduleStatus.PENDING)
                .requestReason(request.reason().trim())
                .previousSelectedStartTime(booking.getSelectedStartTime())
                .previousSelectedEndTime(booking.getSelectedEndTime())
                .proposedSelectedStartTime(request.proposedSelectedStartTime())
                .proposedSelectedEndTime(request.proposedSelectedEndTime())
                .requestedAt(DateTimeUtil.now())
                .build());
        notifyCreate(entity);
        return toResponse(entity);
    }

    private BookingRescheduleRequestResponse acceptInternal(BookingRescheduleRequest rescheduleRequest,
                                                            UUID responderUserId,
                                                            BookingRescheduleActorRole responderRole,
                                                            String reason,
                                                            boolean adminOverride) {
        BookingRescheduleStatus previousStatus = rescheduleRequest.getStatus();
        Booking booking = rescheduleRequest.getBooking();
        validateRescheduleableBooking(booking);
        ensureWithinRescheduleResponseWindow(booking);

        UUID proposedSlotId = rescheduleRequest.getProposedSlot().getId();
        UUID currentSlotId = rescheduleRequest.getCurrentSlot().getId();
        MentorAvailabilitySlot proposedSlot = mentorAvailabilitySlotRepository.findByIdForUpdate(proposedSlotId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy slot mới tại thời điểm chấp nhận"));
        MentorAvailabilitySlot currentSlot = currentSlotId.equals(proposedSlotId)
                ? proposedSlot
                : mentorAvailabilitySlotRepository.findByIdForUpdate(currentSlotId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy slot cũ tại thời điểm chấp nhận"));

        validateProposedSlotForBooking(booking, proposedSlot);
        bookingSlotValidator.validateSelectedRange(
                proposedSlot,
                booking.getService(),
                rescheduleRequest.getProposedSelectedStartTime(),
                rescheduleRequest.getProposedSelectedEndTime(),
                DateTimeUtil.now()
        );
        bookingSlotValidator.validateServiceAttachedToSlot(proposedSlot.getId(), booking.getService().getId());
        bookingSlotValidator.validateCandidateSelection(
                proposedSlot,
                booking.getService(),
                booking.getMentee().getId(),
                rescheduleRequest.getProposedSelectedStartTime(),
                rescheduleRequest.getProposedSelectedEndTime()
        );

        List<Booking> overlappingPendingBookings = bookingRepository.findOverlappingBySlotIdAndStatusForUpdate(
                proposedSlot.getId(),
                BookingStatus.PENDING,
                rescheduleRequest.getProposedSelectedStartTime(),
                rescheduleRequest.getProposedSelectedEndTime()
        );

        booking.setSlot(proposedSlot);
        booking.setSelectedStartTime(rescheduleRequest.getProposedSelectedStartTime());
        booking.setSelectedEndTime(rescheduleRequest.getProposedSelectedEndTime());
        booking.setRequestedStartTime(rescheduleRequest.getProposedSelectedStartTime());
        booking.setRequestedEndTime(rescheduleRequest.getProposedSelectedEndTime());
        if (booking.getStatus() == BookingStatus.PAID) {
            booking.setStatus(BookingStatus.PAID);
        } else {
            booking.setStatus(BookingStatus.ACCEPTED);
        }
        booking.setUpdatedAt(DateTimeUtil.now());
        booking.setRescheduleCount((booking.getRescheduleCount() == null ? 0 : booking.getRescheduleCount()) + 1);
        bookingRepository.save(booking);

        LocalDateTime now = DateTimeUtil.now();
        for (Booking pendingBooking : overlappingPendingBookings) {
            pendingBooking.setStatus(BookingStatus.REJECTED);
            pendingBooking.setRejectedAt(now);
            pendingBooking.setRejectReason("Khung giờ này không còn khả dụng sau khi booking khác được dời lịch vào cùng segment.");
        }
        if (!overlappingPendingBookings.isEmpty()) {
            bookingRepository.saveAll(overlappingPendingBookings);
        }

        rescheduleRequest.setStatus(BookingRescheduleStatus.ACCEPTED);
        rescheduleRequest.setRespondedByUserId(responderUserId);
        rescheduleRequest.setResponderRole(responderRole);
        rescheduleRequest.setRespondedAt(DateTimeUtil.now());
        rescheduleRequest.setResponseNote(trimReason(reason, adminOverride
                ? "Admin đã force approve reschedule request."
                : "Đồng ý dời lịch."));
        rescheduleRequest.setAdminOverride(adminOverride);
        bookingRescheduleRequestRepository.save(rescheduleRequest);
        if (adminOverride) {
            saveAdminAuditLog(responderUserId, AuditAction.APPROVE, previousStatus, rescheduleRequest, reason);
        }

        refreshSlotBookedFlag(currentSlot);
        refreshSlotBookedFlag(proposedSlot);
        notifyAccept(rescheduleRequest);
        notifyAutoRejectedPendingBookings(overlappingPendingBookings);
        return toResponse(rescheduleRequest);
    }

    private BookingRescheduleRequestResponse rejectInternal(BookingRescheduleRequest rescheduleRequest,
                                                            UUID responderUserId,
                                                            BookingRescheduleActorRole responderRole,
                                                            String reason,
                                                            boolean adminOverride) {
        BookingRescheduleStatus previousStatus = rescheduleRequest.getStatus();
        rescheduleRequest.setStatus(BookingRescheduleStatus.REJECTED);
        rescheduleRequest.setRespondedByUserId(responderUserId);
        rescheduleRequest.setResponderRole(responderRole);
        rescheduleRequest.setRespondedAt(DateTimeUtil.now());
        rescheduleRequest.setResponseNote(trimReason(reason, adminOverride
                ? "Admin đã force reject reschedule request."
                : "Từ chối dời lịch."));
        rescheduleRequest.setAdminOverride(adminOverride);
        bookingRescheduleRequestRepository.save(rescheduleRequest);
        if (adminOverride) {
            saveAdminAuditLog(responderUserId, AuditAction.REJECT, previousStatus, rescheduleRequest, reason);
        }
        notifyReject(rescheduleRequest);
        return toResponse(rescheduleRequest);
    }

    private BookingRescheduleRequest loadPendingRequestForResponse(UUID requestId) {
        BookingRescheduleRequest request = bookingRescheduleRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy reschedule request"));
        if (request.getStatus() != BookingRescheduleStatus.PENDING) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Reschedule request hiện không còn ở trạng thái chờ phản hồi");
        }
        if (isPastResponseDeadline(request.getBooking(), DateTimeUtil.now())) {
            request.setStatus(BookingRescheduleStatus.EXPIRED);
            request.setExpiredAt(DateTimeUtil.now());
            request.setRespondedAt(DateTimeUtil.now());
            request.setResponseNote("Reschedule request đã hết hạn do đã qua mốc 2 giờ trước giờ học cũ.");
            bookingRescheduleRequestRepository.save(request);
            notifyExpire(request);
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Reschedule request đã hết hạn");
        }
        return request;
    }

    private void validateCreatePermission(Booking booking, UUID currentUserId, BookingRescheduleActorRole actorRole) {
        switch (actorRole) {
            case MENTEE -> {
                if (booking.getMentee() == null || !currentUserId.equals(booking.getMentee().getId())) {
                    throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền tạo reschedule request cho booking này");
                }
            }
            case MENTOR -> {
                if (booking.getMentorProfile() == null || !currentUserId.equals(booking.getMentorProfile().getUserId())) {
                    throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền tạo reschedule request cho booking này");
                }
            }
        }
    }

    private BookingRescheduleActorRole resolveParticipantResponderRole(Booking booking, UUID currentUserId) {
        if (booking.getMentee() != null && currentUserId.equals(booking.getMentee().getId())) {
            return BookingRescheduleActorRole.MENTEE;
        }
        if (booking.getMentorProfile() != null && currentUserId.equals(booking.getMentorProfile().getUserId())) {
            return BookingRescheduleActorRole.MENTOR;
        }
        throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền phản hồi reschedule request của booking này");
    }

    private void assertParticipantAccess(Booking booking, UUID currentUserId) {
        if ((booking.getMentee() != null && currentUserId.equals(booking.getMentee().getId()))
                || (booking.getMentorProfile() != null && currentUserId.equals(booking.getMentorProfile().getUserId()))) {
            return;
        }
        throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền xem reschedule history của booking này");
    }

    private void validateRescheduleableBooking(Booking booking) {
        if (booking.getStatus() != BookingStatus.PAID && booking.getStatus() != BookingStatus.ACCEPTED) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ booking đã xác nhận mới được reschedule");
        }
        if (booking.getService() == null || booking.getService().getId() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Booking hiện không gắn với service hợp lệ để reschedule");
        }
        if ((booking.getRescheduleCount() == null ? 0 : booking.getRescheduleCount()) >= MAX_RESCHEDULE_COUNT) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking này đã dùng hết quota reschedule");
        }
    }

    private void ensureWithinRescheduleWindow(Booking booking) {
        if (booking.getSelectedStartTime() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Booking hiện không có thời gian bắt đầu hợp lệ");
        }
        long minutesUntilStart = Duration.between(DateTimeUtil.now(), booking.getSelectedStartTime()).toMinutes();
        if (minutesUntilStart < RESCHEDULE_DEADLINE_MINUTES) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ được reschedule trước giờ học ít nhất 6 giờ");
        }
    }

    private void ensureWithinRescheduleResponseWindow(Booking booking) {
        if (isPastResponseDeadline(booking, DateTimeUtil.now())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Reschedule request đã quá hạn phản hồi vì đã qua mốc 2 giờ trước giờ học cũ");
        }
    }

    private boolean isPastResponseDeadline(Booking booking, LocalDateTime now) {
        if (booking == null || booking.getSelectedStartTime() == null) {
            return true;
        }
        return !now.isBefore(booking.getSelectedStartTime().minusHours(2));
    }

    private void validateProposedSlotForBooking(Booking booking, MentorAvailabilitySlot proposedSlot) {
        if (proposedSlot.getMentorProfile() == null || booking.getMentorProfile() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Slot mới hoặc booking hiện không gắn với mentor hợp lệ");
        }
        if (!booking.getMentorProfile().getUserId().equals(proposedSlot.getMentorProfile().getUserId())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Reschedule không được đổi sang mentor khác");
        }
        if (!proposedSlot.isActive()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Slot mới hiện không còn active");
        }
    }

    private boolean sameSegment(Booking booking,
                                MentorAvailabilitySlot proposedSlot,
                                LocalDateTime proposedStart,
                                LocalDateTime proposedEnd) {
        return booking.getSlot() != null
                && proposedSlot != null
                && booking.getSlot().getId().equals(proposedSlot.getId())
                && proposedStart != null
                && proposedEnd != null
                && proposedStart.equals(booking.getSelectedStartTime())
                && proposedEnd.equals(booking.getSelectedEndTime());
    }

    private void refreshSlotBookedFlag(MentorAvailabilitySlot slot) {
        if (slot == null || slot.getId() == null) {
            return;
        }
        boolean hasAcceptedBookings = bookingRepository.existsOverlappingBySlotIdAndStatusIn(
                slot.getId(),
                List.of(BookingStatus.ACCEPTED_AWAITING_PAYMENT, BookingStatus.ACCEPTED, BookingStatus.PAID),
                slot.getStartTime(),
                slot.getEndTime()
        );
        slot.setBooked(hasAcceptedBookings);
    }

    private void notifyCreate(BookingRescheduleRequest request) {
        Booking booking = request.getBooking();
        UUID recipientId = request.getRequesterRole() == BookingRescheduleActorRole.MENTEE
                ? booking.getMentorProfile().getUserId()
                : booking.getMentee().getId();
        if (request.getRequesterRole() == BookingRescheduleActorRole.ADMIN) {
            notificationService.createNotification(
                    booking.getMentee().getId(),
                    NotificationType.BOOKING_RESCHEDULE_REQUESTED,
                    "Admin đã tạo đề xuất dời lịch",
                    "Admin đã tạo một reschedule request cho booking của bạn.",
                    "BOOKING",
                    booking.getId()
            );
            notificationService.createNotification(
                    booking.getMentorProfile().getUserId(),
                    NotificationType.BOOKING_RESCHEDULE_REQUESTED,
                    "Admin đã tạo đề xuất dời lịch",
                    "Admin đã tạo một reschedule request cho booking của bạn.",
                    "BOOKING",
                    booking.getId()
            );
            return;
        }
        notificationService.createNotification(
                recipientId,
                NotificationType.BOOKING_RESCHEDULE_REQUESTED,
                "Có đề xuất dời lịch mới",
                "Booking của bạn vừa có một đề xuất dời lịch mới và đang chờ phản hồi.",
                "BOOKING",
                booking.getId()
        );
    }

    private void notifyAccept(BookingRescheduleRequest request) {
        Booking booking = request.getBooking();
        if (request.isAdminOverride()) {
            notifyBothParticipants(
                    booking,
                    NotificationType.BOOKING_RESCHEDULE_ACCEPTED,
                    "Đề xuất dời lịch đã được chấp nhận",
                    "Booking của bạn đã được dời sang lịch mới."
            );
            return;
        }
        UUID recipientId = request.getRequesterRole() == BookingRescheduleActorRole.MENTEE
                ? booking.getMentee().getId()
                : booking.getMentorProfile().getUserId();
        notificationService.createNotification(
                recipientId,
                NotificationType.BOOKING_RESCHEDULE_ACCEPTED,
                "Đề xuất dời lịch đã được chấp nhận",
                "Booking của bạn đã được dời sang lịch mới.",
                "BOOKING",
                booking.getId()
        );
    }

    private void notifyReject(BookingRescheduleRequest request) {
        Booking booking = request.getBooking();
        if (request.isAdminOverride()) {
            notifyBothParticipants(
                    booking,
                    NotificationType.BOOKING_RESCHEDULE_REJECTED,
                    "Đề xuất dời lịch đã bị từ chối",
                    "Reschedule request của booking đã bị từ chối. Lịch cũ vẫn được giữ nguyên."
            );
            return;
        }
        UUID recipientId = request.getRequesterRole() == BookingRescheduleActorRole.MENTEE
                ? booking.getMentee().getId()
                : booking.getMentorProfile().getUserId();
        notificationService.createNotification(
                recipientId,
                NotificationType.BOOKING_RESCHEDULE_REJECTED,
                "Đề xuất dời lịch đã bị từ chối",
                "Reschedule request của booking đã bị từ chối. Lịch cũ vẫn được giữ nguyên.",
                "BOOKING",
                booking.getId()
        );
    }

    private void notifyExpire(BookingRescheduleRequest request) {

        Booking booking = request.getBooking();
        notificationService.createNotification(
                booking.getMentee().getId(),
                NotificationType.BOOKING_RESCHEDULE_EXPIRED,
                "Đề xuất dời lịch đã hết hạn",
                "Reschedule request của booking đã hết hạn vì chưa được phản hồi trước giờ học cũ.",
                "BOOKING",
                booking.getId()
        );
        notificationService.createNotification(
                booking.getMentorProfile().getUserId(),
                NotificationType.BOOKING_RESCHEDULE_EXPIRED,
                "Đề xuất dời lịch đã hết hạn",
                "Reschedule request của booking đã hết hạn vì chưa được phản hồi trước giờ học cũ.",
                "BOOKING",
                booking.getId()
        );
    }

    private void notifyBothParticipants(Booking booking, NotificationType type, String title, String message) {
        notificationService.createNotification(
                booking.getMentee().getId(),
                type,
                title,
                message,
                "BOOKING",
                booking.getId()
        );
        notificationService.createNotification(
                booking.getMentorProfile().getUserId(),
                type,
                title,
                message,
                "BOOKING",
                booking.getId()
        );
    }

    private void notifyAutoRejectedPendingBookings(List<Booking> pendingBookings) {
        for (Booking pendingBooking : pendingBookings) {
            notificationService.createNotification(
                    pendingBooking.getMentee().getId(),
                    NotificationType.BOOKING_AUTO_REJECTED,
                    "Yêu cầu đặt lịch không còn khả dụng",
                    "Khung giờ này đã được một booking khác sử dụng sau khi dời lịch.",
                    "BOOKING",
                    pendingBooking.getId()
            );
        }
    }

    private String trimReason(String reason, String fallback) {
        if (reason == null || reason.trim().isBlank()) {
            return fallback;
        }
        return reason.trim();
    }

    private void saveAdminAuditLog(UUID adminUserId,
                                   AuditAction action,
                                   BookingRescheduleStatus previousStatus,
                                   BookingRescheduleRequest request,
                                   String reason) {
        User adminUser = userRepository.findById(adminUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy admin thực hiện override"));
        String oldValue = AuditLogJsonUtil.toJson(Map.of(
                "status", previousStatus == null ? "" : previousStatus.name(),
                "bookingId", request.getBooking().getId().toString(),
                "from", request.getPreviousSelectedStartTime() == null ? "" : request.getPreviousSelectedStartTime().toString(),
                "to", request.getProposedSelectedStartTime() == null ? "" : request.getProposedSelectedStartTime().toString()
        ));
        String newValue = AuditLogJsonUtil.toJson(Map.of(
                "status", action == AuditAction.APPROVE ? BookingRescheduleStatus.ACCEPTED.name() : BookingRescheduleStatus.REJECTED.name(),
                "reason", reason == null ? "" : reason,
                "adminOverride", true
        ));
        auditLogRepository.save(AuditLog.builder()
                .actor(adminUser)
                .action(action)
                .entityType("BOOKING_RESCHEDULE_REQUEST")
                .entityId(request.getId())
                .oldValue(oldValue)
                .newValue(newValue)
                .build());
    }

    private BookingRescheduleRequestResponse toResponse(BookingRescheduleRequest request) {
        return BookingRescheduleRequestResponse.builder()
                .rescheduleRequestId(request.getId())
                .bookingId(request.getBooking().getId())
                .currentSlotId(request.getCurrentSlot().getId())
                .proposedSlotId(request.getProposedSlot().getId())
                .previousSelectedStartTime(request.getPreviousSelectedStartTime())
                .previousSelectedEndTime(request.getPreviousSelectedEndTime())
                .proposedSelectedStartTime(request.getProposedSelectedStartTime())
                .proposedSelectedEndTime(request.getProposedSelectedEndTime())
                .requesterRole(request.getRequesterRole() == null ? null : request.getRequesterRole().name())
                .requestedByUserId(request.getRequestedByUserId())
                .responderRole(request.getResponderRole() == null ? null : request.getResponderRole().name())
                .respondedByUserId(request.getRespondedByUserId())
                .status(request.getStatus() == null ? null : request.getStatus().name())
                .requestReason(request.getRequestReason())
                .responseNote(request.getResponseNote())
                .adminOverride(request.isAdminOverride())
                .requestedAt(request.getRequestedAt())
                .respondedAt(request.getRespondedAt())
                .expiredAt(request.getExpiredAt())
                .build();
    }
}
