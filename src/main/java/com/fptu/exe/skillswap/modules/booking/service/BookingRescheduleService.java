package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingRescheduleActorRole;
import com.fptu.exe.skillswap.modules.booking.domain.BookingRescheduleRequest;
import com.fptu.exe.skillswap.modules.booking.domain.BookingRescheduleStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionCommand;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionExecutor;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateBookingRescheduleRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.RespondBookingRescheduleRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingRescheduleRequestResponse;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRescheduleRequestRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.booking.event.BookingCalendarLifecycleEvent;
import com.fptu.exe.skillswap.modules.mentor.port.MentorBookingQueryPort;
import com.fptu.exe.skillswap.modules.mentor.port.ServiceSlotCandidate;
import com.fptu.exe.skillswap.modules.notification.NotificationEvent;
import com.fptu.exe.skillswap.modules.notification.NotificationType;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
    private final MentorBookingQueryPort mentorBookingQueryPort;
    private final SessionService sessionService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    private TimeProvider timeProvider = TimeProvider.from(Clock.systemUTC());

    @Autowired(required = false)
    public void setTimeProvider(TimeProvider timeProvider) {
        if (timeProvider != null) {
            this.timeProvider = timeProvider;
        }
    }

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
        LocalDateTime now = timeProvider.nowBusiness();
        Instant nowUtc = timeProvider.instant();
        int expired = 0;
        for (BookingRescheduleRequest request : bookingRescheduleRequestRepository.findExpirablePendingRequests(
                BookingRescheduleStatus.PENDING,
                now.plusHours(2)
        )) {
            if (request.getStatus() != BookingRescheduleStatus.PENDING) {
                continue;
            }
            if (isPastResponseDeadline(request.getBooking(), nowUtc)) {
                request.setStatus(BookingRescheduleStatus.EXPIRED);
                request.setExpiredAtUtc(nowUtc);
                request.setExpiredAt(now);
                request.setRespondedAtUtc(nowUtc);
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
        Instant proposedStartUtc = request.proposedSelectedStartTime().toInstant();
        Instant proposedEndUtc = request.proposedSelectedEndTime().toInstant();
        ServiceSlotCandidate serviceCandidate = mentorBookingQueryPort.getActiveServiceCandidate(booking.getServiceId(), booking.getMentorUserId())
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_CONFLICT, "Gói mentoring hiện không còn khả dụng"));
        bookingSlotValidator.validateSelectedRange(
                proposedSlot,
                serviceCandidate,
                proposedStartUtc,
                proposedEndUtc,
                timeProvider.instant()
        );
        bookingSlotValidator.validateServiceAttachedToSlot(proposedSlot.getId(), booking.getServiceId());

        if (sameSegment(booking, proposedSlot, BookingTime.fromInstant(proposedStartUtc), BookingTime.fromInstant(proposedEndUtc))) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Lịch mới phải khác lịch hiện tại của booking");
        }

        LocalDateTime nowBusiness = timeProvider.nowBusiness();
        Instant nowUtc = timeProvider.instant();

        BookingRescheduleRequest entity = bookingRescheduleRequestRepository.save(BookingRescheduleRequest.builder()
                .booking(booking)
                .currentSlot(currentSlot)
                .proposedSlot(proposedSlot)
                .requestedByUserId(currentUserId)
                .requesterRole(actorRole)
                .status(BookingRescheduleStatus.PENDING)
                .requestReason(request.reason().trim())
                .previousSelectedStartTime(booking.getSelectedStartTime())
                .previousSelectedStartTimeUtc(BookingTime.resolveSelectedStartUtc(booking))
                .previousSelectedEndTime(booking.getSelectedEndTime())
                .previousSelectedEndTimeUtc(BookingTime.resolveSelectedEndUtc(booking))
                .proposedSelectedStartTime(BookingTime.fromInstant(proposedStartUtc))
                .proposedSelectedStartTimeUtc(proposedStartUtc)
                .proposedSelectedEndTime(BookingTime.fromInstant(proposedEndUtc))
                .proposedSelectedEndTimeUtc(proposedEndUtc)
                .requestedAt(nowBusiness)
                .requestedAtUtc(nowUtc)
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
        Booking booking = bookingRepository.findByIdForSessionUpdate(rescheduleRequest.getBooking().getId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
        validateRescheduleableBooking(booking);
        ensureWithinRescheduleResponseWindow(booking);

        UUID proposedSlotId = rescheduleRequest.getProposedSlot().getId();
        UUID currentSlotId = rescheduleRequest.getCurrentSlot().getId();
        Map<UUID, MentorAvailabilitySlot> lockedSlots = new java.util.LinkedHashMap<>();
        java.util.stream.Stream.of(currentSlotId, proposedSlotId)
                .distinct()
                .sorted()
                .forEach(slotId -> lockedSlots.put(slotId, mentorAvailabilitySlotRepository.findByIdForUpdate(slotId)
                        .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy slot tại thời điểm chấp nhận"))));
        MentorAvailabilitySlot proposedSlot = lockedSlots.get(proposedSlotId);
        MentorAvailabilitySlot currentSlot = lockedSlots.get(currentSlotId);

        validateProposedSlotForBooking(booking, proposedSlot);
        ServiceSlotCandidate serviceCandidate = mentorBookingQueryPort.getActiveServiceCandidate(booking.getServiceId(), booking.getMentorUserId())
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_CONFLICT, "Gói mentoring hiện không còn khả dụng"));
        bookingSlotValidator.validateSelectedRange(
                proposedSlot,
                serviceCandidate,
                rescheduleRequest.getProposedSelectedStartTime(),
                rescheduleRequest.getProposedSelectedEndTime(),
                timeProvider.nowBusiness()
        );
        bookingSlotValidator.validateServiceAttachedToSlot(proposedSlot.getId(), booking.getServiceId());
        bookingSlotValidator.validateCandidateSelection(
                proposedSlot,
                serviceCandidate,
                booking.getMenteeUserId(),
                rescheduleRequest.getProposedSelectedStartTime(),
                rescheduleRequest.getProposedSelectedEndTime()
        );

        List<Booking> overlappingPendingBookings = bookingRepository.findOverlappingBySlotIdAndStatusForUpdateUtc(
                proposedSlot.getId(),
                BookingStatus.PENDING,
                BookingTime.toInstant(rescheduleRequest.getProposedSelectedStartTime()),
                BookingTime.toInstant(rescheduleRequest.getProposedSelectedEndTime())
        );

        Instant nowUtc = timeProvider.instant();
        LocalDateTime nowBusiness = timeProvider.nowBusiness();

        booking.setSlot(proposedSlot);
        booking.setSelectedStartTime(rescheduleRequest.getProposedSelectedStartTime());
        booking.setSelectedStartTimeUtc(BookingTime.toInstant(rescheduleRequest.getProposedSelectedStartTime()));
        booking.setSelectedEndTime(rescheduleRequest.getProposedSelectedEndTime());
        booking.setSelectedEndTimeUtc(BookingTime.toInstant(rescheduleRequest.getProposedSelectedEndTime()));
        booking.setRequestedStartTime(rescheduleRequest.getProposedSelectedStartTime());
        booking.setRequestedEndTime(rescheduleRequest.getProposedSelectedEndTime());
        booking.setUpdatedAt(nowBusiness);
        booking.setRescheduleCount((booking.getRescheduleCount() == null ? 0 : booking.getRescheduleCount()) + 1);
        bookingRepository.save(booking);
        sessionService.updateScheduleForBooking(
                booking.getId(),
                BookingTime.toInstant(rescheduleRequest.getProposedSelectedStartTime()),
                BookingTime.toInstant(rescheduleRequest.getProposedSelectedEndTime())
        );

        for (Booking pendingBooking : overlappingPendingBookings) {
            BookingTransitionExecutor.apply(pendingBooking, BookingTransitionCommand.SYSTEM_REJECT, nowUtc);
            pendingBooking.setRejectReason("Khung giờ này không còn khả dụng sau khi booking khác được dời lịch vào cùng segment.");
        }
        if (!overlappingPendingBookings.isEmpty()) {
            bookingRepository.saveAll(overlappingPendingBookings);
        }

        rescheduleRequest.setStatus(BookingRescheduleStatus.ACCEPTED);
        rescheduleRequest.setRespondedByUserId(responderUserId);
        rescheduleRequest.setResponderRole(responderRole);
        rescheduleRequest.setRespondedAt(nowBusiness);
        rescheduleRequest.setRespondedAtUtc(nowUtc);
        rescheduleRequest.setResponseNote(trimReason(reason, adminOverride
                ? "Admin đã force approve reschedule request."
                : "Đồng ý dời lịch."));
        rescheduleRequest.setAdminOverride(adminOverride);
        bookingRescheduleRequestRepository.save(rescheduleRequest);

        refreshSlotBookedFlag(currentSlot);
        refreshSlotBookedFlag(proposedSlot);
        eventPublisher.publishEvent(BookingCalendarLifecycleEvent.of(booking.getId(), booking.getMentorUserId(), BookingCalendarLifecycleEvent.Action.UPDATE));
        notifyAccept(rescheduleRequest);
        notifyAutoRejectedPendingBookings(overlappingPendingBookings);
        return toResponse(rescheduleRequest);
    }

    private BookingRescheduleRequestResponse rejectInternal(BookingRescheduleRequest rescheduleRequest,
                                                            UUID responderUserId,
                                                            BookingRescheduleActorRole responderRole,
                                                            String reason,
                                                            boolean adminOverride) {
        LocalDateTime nowBusiness = timeProvider.nowBusiness();
        Instant nowUtc = timeProvider.instant();
        BookingRescheduleStatus previousStatus = rescheduleRequest.getStatus();
        rescheduleRequest.setStatus(BookingRescheduleStatus.REJECTED);
        rescheduleRequest.setRespondedByUserId(responderUserId);
        rescheduleRequest.setResponderRole(responderRole);
        rescheduleRequest.setRespondedAt(nowBusiness);
        rescheduleRequest.setRespondedAtUtc(nowUtc);
        rescheduleRequest.setResponseNote(trimReason(reason, adminOverride
                ? "Admin đã force reject reschedule request."
                : "Từ chối dời lịch."));
        rescheduleRequest.setAdminOverride(adminOverride);
        bookingRescheduleRequestRepository.save(rescheduleRequest);
        notifyReject(rescheduleRequest);
        return toResponse(rescheduleRequest);
    }

    private BookingRescheduleRequest loadPendingRequestForResponse(UUID requestId) {
        BookingRescheduleRequest request = bookingRescheduleRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy reschedule request"));
        if (request.getStatus() != BookingRescheduleStatus.PENDING) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Reschedule request hiện không còn ở trạng thái chờ phản hồi");
        }
        Instant nowUtc = timeProvider.instant();
        LocalDateTime nowBusiness = timeProvider.nowBusiness();
        if (isPastResponseDeadline(request.getBooking(), nowUtc)) {
            request.setStatus(BookingRescheduleStatus.EXPIRED);
            request.setExpiredAtUtc(nowUtc);
            request.setExpiredAt(nowBusiness);
            request.setRespondedAtUtc(nowUtc);
            request.setRespondedAt(nowBusiness);
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
                if (booking.getMenteeUserId() == null || !currentUserId.equals(booking.getMenteeUserId())) {
                    throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền tạo reschedule request cho booking này");
                }
            }
            case MENTOR -> {
                if (booking.getMentorUserId() == null || !currentUserId.equals(booking.getMentorUserId())) {
                    throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền tạo reschedule request cho booking này");
                }
            }
        }
    }

    private BookingRescheduleActorRole resolveParticipantResponderRole(Booking booking, UUID currentUserId) {
        if (booking.getMenteeUserId() != null && currentUserId.equals(booking.getMenteeUserId())) {
            return BookingRescheduleActorRole.MENTEE;
        }
        if (booking.getMentorUserId() != null && currentUserId.equals(booking.getMentorUserId())) {
            return BookingRescheduleActorRole.MENTOR;
        }
        throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền phản hồi reschedule request của booking này");
    }

    private void assertParticipantAccess(Booking booking, UUID currentUserId) {
        if ((booking.getMenteeUserId() != null && currentUserId.equals(booking.getMenteeUserId()))
                || (booking.getMentorUserId() != null && currentUserId.equals(booking.getMentorUserId()))) {
            return;
        }
        throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền xem reschedule history của booking này");
    }

    private void validateRescheduleableBooking(Booking booking) {
        if (booking.getStatus() != BookingStatus.PAID) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ booking đã thanh toán và chưa bắt đầu mới được reschedule");
        }
        if (booking.getServiceId() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Booking hiện không gắn với service hợp lệ để reschedule");
        }
        if ((booking.getRescheduleCount() == null ? 0 : booking.getRescheduleCount()) >= MAX_RESCHEDULE_COUNT) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking này đã dùng hết quota reschedule");
        }
    }

    private void ensureWithinRescheduleWindow(Booking booking) {
        Instant startUtc = BookingTime.resolveSelectedStartUtc(booking);
        if (startUtc == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Booking hiện không có thời gian bắt đầu hợp lệ");
        }
        long minutesUntilStart = Duration.between(timeProvider.instant(), startUtc).toMinutes();
        if (minutesUntilStart < RESCHEDULE_DEADLINE_MINUTES) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ được reschedule trước giờ học ít nhất 6 giờ");
        }
    }

    private void ensureWithinRescheduleResponseWindow(Booking booking) {
        if (isPastResponseDeadline(booking, timeProvider.instant())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Reschedule request đã quá hạn phản hồi vì đã qua mốc 2 giờ trước giờ học cũ");
        }
    }

    private boolean isPastResponseDeadline(Booking booking, Instant nowUtc) {
        Instant startUtc = BookingTime.resolveSelectedStartUtc(booking);
        if (startUtc == null) {
            return true;
        }
        return !nowUtc.isBefore(startUtc.minus(Duration.ofHours(2)));
    }

    private boolean isPastResponseDeadline(Booking booking, LocalDateTime now) {
        return isPastResponseDeadline(booking, now != null ? BookingTime.toInstant(now) : timeProvider.instant());
    }

    private void validateProposedSlotForBooking(Booking booking, MentorAvailabilitySlot proposedSlot) {
        if (proposedSlot.getMentorUserId() == null || booking.getMentorUserId() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Slot mới hoặc booking hiện không gắn với mentor hợp lệ");
        }
        if (!booking.getMentorUserId().equals(proposedSlot.getMentorUserId())) {
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
        Instant startUtc = slot.getStartTimeUtc() != null ? slot.getStartTimeUtc()
                : (slot.getStartTime() == null ? null : BookingTime.toInstant(slot.getStartTime()));
        Instant endUtc = slot.getEndTimeUtc() != null ? slot.getEndTimeUtc()
                : (slot.getEndTime() == null ? null : BookingTime.toInstant(slot.getEndTime()));
        boolean hasAcceptedBookings = startUtc != null && endUtc != null
                && bookingRepository.existsOverlappingBySlotIdAndStatusInUtc(
                slot.getId(),
                List.of(BookingStatus.ACCEPTED_AWAITING_PAYMENT, BookingStatus.PAID),
                startUtc,
                endUtc
        );
        slot.setBooked(hasAcceptedBookings);
    }

    private void notifyCreate(BookingRescheduleRequest request) {
        Booking booking = request.getBooking();
        UUID recipientId = request.getRequesterRole() == BookingRescheduleActorRole.MENTEE
                ? booking.getMentorUserId()
                : booking.getMenteeUserId();
        if (request.getRequesterRole() == BookingRescheduleActorRole.ADMIN) {
            eventPublisher.publishEvent(new NotificationEvent(
                    booking.getMenteeUserId(),
                    NotificationType.BOOKING_RESCHEDULE_REQUESTED,
                    "Admin đã tạo đề xuất dời lịch",
                    "Admin đã tạo một reschedule request cho booking của bạn.",
                    "BOOKING",
                    booking.getId()
            ));
            eventPublisher.publishEvent(new NotificationEvent(
                    booking.getMentorUserId(),
                    NotificationType.BOOKING_RESCHEDULE_REQUESTED,
                    "Admin đã tạo đề xuất dời lịch",
                    "Admin đã tạo một reschedule request cho booking của bạn.",
                    "BOOKING",
                    booking.getId()
            ));
            return;
        }
        eventPublisher.publishEvent(new NotificationEvent(
                recipientId,
                NotificationType.BOOKING_RESCHEDULE_REQUESTED,
                "Có đề xuất dời lịch mới",
                "Booking của bạn vừa có một đề xuất dời lịch mới và đang chờ phản hồi.",
                "BOOKING",
                booking.getId()
        ));
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
                ? booking.getMenteeUserId()
                : booking.getMentorUserId();
        eventPublisher.publishEvent(new NotificationEvent(
                recipientId,
                NotificationType.BOOKING_RESCHEDULE_ACCEPTED,
                "Đề xuất dời lịch đã được chấp nhận",
                "Booking của bạn đã được dời sang lịch mới.",
                "BOOKING",
                booking.getId()
        ));

        eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent(
                booking.getId(),
                booking.getMenteeUserId(),
                booking.getMentorUserId(),
                booking.getStatus(),
                "Yêu cầu dời lịch đã được chấp nhận.",
                booking.getUpdatedAt() != null ? booking.getUpdatedAt() : timeProvider.nowBusiness()
        ));
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
                ? booking.getMenteeUserId()
                : booking.getMentorUserId();
        eventPublisher.publishEvent(new NotificationEvent(
                recipientId,
                NotificationType.BOOKING_RESCHEDULE_REJECTED,
                "Đề xuất dời lịch đã bị từ chối",
                "Reschedule request của booking đã bị từ chối. Lịch cũ vẫn được giữ nguyên.",
                "BOOKING",
                booking.getId()
        ));

        eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent(
                booking.getId(),
                booking.getMenteeUserId(),
                booking.getMentorUserId(),
                booking.getStatus(),
                "Yêu cầu dời lịch đã bị từ chối.",
                booking.getUpdatedAt() != null ? booking.getUpdatedAt() : timeProvider.nowBusiness()
        ));
    }

    private void notifyExpire(BookingRescheduleRequest request) {
        Booking booking = request.getBooking();
        eventPublisher.publishEvent(new NotificationEvent(
                booking.getMenteeUserId(),
                NotificationType.BOOKING_RESCHEDULE_EXPIRED,
                "Đề xuất dời lịch đã hết hạn",
                "Reschedule request của booking đã hết hạn vì chưa được phản hồi trước giờ học cũ.",
                "BOOKING",
                booking.getId()
        ));
        eventPublisher.publishEvent(new NotificationEvent(
                booking.getMentorUserId(),
                NotificationType.BOOKING_RESCHEDULE_EXPIRED,
                "Đề xuất dời lịch đã hết hạn",
                "Reschedule request của booking đã hết hạn vì chưa được phản hồi trước giờ học cũ.",
                "BOOKING",
                booking.getId()
        ));

        eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent(
                booking.getId(),
                booking.getMenteeUserId(),
                booking.getMentorUserId(),
                booking.getStatus(),
                "Yêu cầu dời lịch đã hết hạn.",
                booking.getUpdatedAt() != null ? booking.getUpdatedAt() : timeProvider.nowBusiness()
        ));
    }

    private void notifyBothParticipants(Booking booking, NotificationType type, String title, String message) {
        eventPublisher.publishEvent(new NotificationEvent(
                booking.getMenteeUserId(),
                type,
                title,
                message,
                "BOOKING",
                booking.getId()
        ));
        eventPublisher.publishEvent(new NotificationEvent(
                booking.getMentorUserId(),
                type,
                title,
                message,
                "BOOKING",
                booking.getId()
        ));
    }

    private void notifyAutoRejectedPendingBookings(List<Booking> pendingBookings) {
        for (Booking pendingBooking : pendingBookings) {
            eventPublisher.publishEvent(new NotificationEvent(
                    pendingBooking.getMenteeUserId(),
                    NotificationType.BOOKING_AUTO_REJECTED,
                    "Yêu cầu đặt lịch không còn khả dụng",
                    "Khung giờ này đã được một booking khác sử dụng sau khi dời lịch.",
                    "BOOKING",
                    pendingBooking.getId()
            ));
        }
    }

    private String trimReason(String reason, String fallback) {
        if (reason == null || reason.trim().isBlank()) {
            return fallback;
        }
        return reason.trim();
    }

    private BookingRescheduleRequestResponse toResponse(BookingRescheduleRequest request) {
        return BookingRescheduleRequestResponse.builder()
                .rescheduleRequestId(request.getId())
                .bookingId(request.getBooking().getId())
                .currentSlotId(request.getCurrentSlot().getId())
                .proposedSlotId(request.getProposedSlot().getId())
                .previousSelectedStartTime(toOdt(request.getPreviousSelectedStartTimeUtc(), request.getPreviousSelectedStartTime()))
                .previousSelectedEndTime(toOdt(request.getPreviousSelectedEndTimeUtc(), request.getPreviousSelectedEndTime()))
                .proposedSelectedStartTime(toOdt(request.getProposedSelectedStartTimeUtc(), request.getProposedSelectedStartTime()))
                .proposedSelectedEndTime(toOdt(request.getProposedSelectedEndTimeUtc(), request.getProposedSelectedEndTime()))
                .requesterRole(request.getRequesterRole() == null ? null : request.getRequesterRole().name())
                .requestedByUserId(request.getRequestedByUserId())
                .responderRole(request.getResponderRole() == null ? null : request.getResponderRole().name())
                .respondedByUserId(request.getRespondedByUserId())
                .status(request.getStatus() == null ? null : request.getStatus().name())
                .requestReason(request.getRequestReason())
                .responseNote(request.getResponseNote())
                .adminOverride(request.isAdminOverride())
                .requestedAt(toOdt(request.getRequestedAtUtc(), request.getRequestedAt()))
                .respondedAt(toOdt(request.getRespondedAtUtc(), request.getRespondedAt()))
                .expiredAt(toOdt(request.getExpiredAtUtc(), request.getExpiredAt()))
                .build();
    }
    private static OffsetDateTime toOdt(Instant utc, LocalDateTime legacy) {
        if (utc != null) return BookingTime.toOffsetDateTime(utc);
        if (legacy != null) return BookingTime.toOffsetDateTime(legacy);
        return null;
    }
}
