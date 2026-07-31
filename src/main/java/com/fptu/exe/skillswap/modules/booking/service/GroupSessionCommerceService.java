package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.GroupSession;
import com.fptu.exe.skillswap.modules.booking.domain.GroupSessionRegistrationStatus;
import com.fptu.exe.skillswap.modules.booking.domain.GroupSessionStatus;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateGroupSessionBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.GroupSessionAttendeeResponse;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.GroupSessionRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.event.NotificationEvent;
import com.fptu.exe.skillswap.modules.payment.service.PaymentOrderService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Owns group-seat capacity. A pending payment booking is the durable seat hold. */
@Service
@RequiredArgsConstructor
public class GroupSessionCommerceService {

    private static final List<BookingStatus> SEAT_LOCKING_STATUSES = List.of(
            BookingStatus.ACCEPTED_AWAITING_PAYMENT, BookingStatus.ACCEPTED, BookingStatus.PAID,
            BookingStatus.AWAITING_MENTOR_COMPLETION, BookingStatus.AWAITING_MENTEE_CONFIRMATION,
            BookingStatus.UNDER_REVIEW
    );

    private final GroupSessionRepository groupSessionRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final PaymentOrderService paymentOrderService;
    private final ApplicationEventPublisher eventPublisher;
    private GroupSessionExperienceService groupSessionExperienceService;

    @Autowired(required = false)
    void setGroupSessionExperienceService(GroupSessionExperienceService groupSessionExperienceService) {
        this.groupSessionExperienceService = groupSessionExperienceService;
    }

    @Transactional
    public Booking createSeat(UUID learnerId, UUID groupSessionId, CreateGroupSessionBookingRequest request) {
        User learner = userRepository.findByIdForUpdate(learnerId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy người dùng"));
        if (learner.getStatus() != UserStatus.ACTIVE) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Tài khoản hiện không thể đặt group session");
        }
        GroupSession session = groupSessionRepository.findByIdForUpdate(groupSessionId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy group session"));
        LocalDateTime now = utcNow();
        requireJoinable(session, learnerId, now);
        if (bookingRepository.existsByMenteeIdAndGroupSessionIdAndStatusIn(learnerId, groupSessionId, SEAT_LOCKING_STATUSES)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Bạn đã có một seat đang hiệu lực trong group session này");
        }
        if (!bookingRepository.findMenteeOverlappingBookingsForUpdate(
                learnerId, SEAT_LOCKING_STATUSES, session.getScheduledStartAt(), session.getScheduledEndAt()).isEmpty()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Bạn đã có lịch mentoring khác trùng thời gian group session");
        }

        boolean free = Boolean.TRUE.equals(session.getServiceIsFreeSnapshot())
                || Integer.valueOf(0).equals(session.getServicePriceScoinSnapshot());
        Booking booking = Booking.builder()
                .mentee(learner)
                .mentorProfile(session.getMentorProfile())
                .service(session.getService())
                .slot(session.getSourceSlot())
                .groupSession(session)
                .status(free ? BookingStatus.PAID : BookingStatus.ACCEPTED_AWAITING_PAYMENT)
                .acceptedAt(now)
                .learningGoalTitle(request.learningGoalTitle().trim())
                .learningGoalDescription(trimToNull(request.learningGoalDescription()))
                .selectedStartTime(session.getScheduledStartAt())
                .selectedEndTime(session.getScheduledEndAt())
                .serviceTitleSnapshot(session.getServiceTitleSnapshot())
                .serviceDescriptionSnapshot(session.getServiceDescriptionSnapshot())
                .serviceExpectedOutcomeSnapshot(session.getServiceExpectedOutcomeSnapshot())
                .serviceDurationSnapshot(session.getServiceDurationSnapshot())
                .serviceIsFreeSnapshot(session.getServiceIsFreeSnapshot())
                .servicePriceScoinSnapshot(session.getServicePriceScoinSnapshot())
                .maintainPostSessionChatSnapshot(false)
                .build();
        session.setReservedSeatCount(session.getReservedSeatCount() + 1);
        Booking saved = bookingRepository.save(booking);
        if (free && groupSessionExperienceService != null) {
            groupSessionExperienceService.activateConfirmedSeat(saved);
        }
        eventPublisher.publishEvent(new NotificationEvent(
                learnerId, NotificationType.BOOKING_ACCEPTED,
                free ? "Seat group session đã được xác nhận" : "Seat đang chờ thanh toán",
                free ? "Bạn đã có seat trong group session." : "Hoàn tất thanh toán trước khi hết hạn để giữ seat.",
                "BOOKING", saved.getId()));
        return saved;
    }

    /** Releases a held/confirmed seat exactly once after its booking becomes terminal. */
    @Transactional
    public void releaseSeatForTerminalTransition(Booking booking, BookingStatus previousStatus) {
        if (booking == null || booking.getGroupSession() == null || !SEAT_LOCKING_STATUSES.contains(previousStatus)) {
            return;
        }
        GroupSession session = groupSessionRepository.findByIdForUpdate(booking.getGroupSession().getId()).orElse(null);
        if (session != null && session.getReservedSeatCount() > 0) {
            session.setReservedSeatCount(session.getReservedSeatCount() - 1);
        }
    }

    /** Called from the mentor group-session cancellation transaction. */
    @Transactional
    public void cancelSeatsForSession(GroupSession session, String reason) {
        List<Booking> seats = bookingRepository.findGroupSeatBookingsForUpdate(session.getId(), SEAT_LOCKING_STATUSES);
        LocalDateTime now = utcNow();
        for (Booking booking : seats) {
            BookingStatus previous = booking.getStatus();
            booking.setStatus(BookingStatus.CANCELLED_BY_MENTOR);
            booking.setCancelledAt(now);
            booking.setCancelReason(reason);
            paymentOrderService.handleMentorCancellation(booking);
            releaseSeatForTerminalTransition(booking, previous);
            if (groupSessionExperienceService != null) {
                groupSessionExperienceService.revokeSeat(booking, false);
            }
            eventPublisher.publishEvent(new NotificationEvent(
                    booking.getMentee().getId(), NotificationType.BOOKING_CANCELLED_BY_MENTOR,
                    "Group session đã bị hủy", "Mentor đã hủy group session. Thanh toán hợp lệ sẽ được hoàn đủ.",
                    "BOOKING", booking.getId()));
        }
    }

    public LocalDateTime resolvePaymentDeadline(Booking booking) {
        if (booking == null || booking.getGroupSession() == null) {
            return null;
        }
        LocalDateTime holdDeadline = BookingDeadlinePolicy.resolvePaymentDeadline(
                booking.getAcceptedAt(), booking.getSelectedStartTime());
        LocalDateTime registrationDeadline = booking.getGroupSession().getRegistrationClosesAt();
        if (holdDeadline == null) return registrationDeadline;
        if (registrationDeadline == null) return holdDeadline;
        return holdDeadline.isBefore(registrationDeadline) ? holdDeadline : registrationDeadline;
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<GroupSessionAttendeeResponse> listRoster(UUID mentorUserId, UUID groupSessionId, Integer limit) {
        GroupSession session = groupSessionRepository.findByIdAndMentorProfileUserId(groupSessionId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy group session"));
        int resolvedLimit = limit == null ? 20 : Math.min(Math.max(1, limit), 20);
        List<GroupSessionAttendeeResponse> items = bookingRepository.findByGroupSessionIdOrderByCreatedAtAsc(session.getId())
                .stream().limit(resolvedLimit).map(booking -> new GroupSessionAttendeeResponse(
                        booking.getId(), booking.getMentee().getId(), booking.getMentee().getFullName(),
                        booking.getMentee().getAvatarUrl(), attendeeState(booking.getStatus()), booking.getCreatedAt()))
                .toList();
        return CursorPageResponse.<GroupSessionAttendeeResponse>builder()
                .items(items).nextCursor(null).prevCursor(null).hasNext(false).hasPrev(false).limit(resolvedLimit).build();
    }

    private void requireJoinable(GroupSession session, UUID learnerId, LocalDateTime now) {
        if (session.getMentorProfile() == null || learnerId.equals(session.getMentorProfile().getUserId())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Bạn không thể đặt seat của group session do chính mình tổ chức");
        }
        if (session.getStatus() != GroupSessionStatus.OPEN
                || session.getRegistrationStatus() != GroupSessionRegistrationStatus.OPEN
                || !session.getRegistrationClosesAt().isAfter(now)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Group session đã đóng đăng ký");
        }
        if (session.getReservedSeatCount() >= session.getMaxParticipants()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Group session đã đủ số lượng seat");
        }
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(DateTimeUtil.getClock().instant(), ZoneOffset.UTC);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String attendeeState(BookingStatus status) {
        if (status == BookingStatus.ACCEPTED_AWAITING_PAYMENT) return "WAITING_PAYMENT";
        if (SEAT_LOCKING_STATUSES.contains(status)) return "CONFIRMED";
        return "CANCELLED";
    }
}
