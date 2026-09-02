package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.constant.BookingQueueConstants;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTime;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionCommand;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionExecutor;
import com.fptu.exe.skillswap.modules.booking.domain.MeetingPlatform;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.dto.request.AcceptBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.RejectBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.event.BookingEmailNotificationEvent;
import com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.booking.service.meeting.MeetingProviderFactory;
import com.fptu.exe.skillswap.modules.booking.port.BookingChatPort;
import com.fptu.exe.skillswap.modules.booking.event.BookingCalendarLifecycleEvent;
import com.fptu.exe.skillswap.modules.identity.port.GoogleCalendarConnectionPort;
import com.fptu.exe.skillswap.modules.identity.port.UserLockPort;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.identity.port.UserSummaryRecord;
import com.fptu.exe.skillswap.modules.notification.NotificationEvent;
import com.fptu.exe.skillswap.modules.notification.NotificationType;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy.resolvePaymentDeadline;

@Service
@RequiredArgsConstructor
public class BookingDecisionService {

    private static final List<BookingStatus> SLOT_LOCKING_STATUSES = List.of(
            BookingStatus.ACCEPTED_AWAITING_PAYMENT,
            BookingStatus.PAID
    );

    private final BookingRepository bookingRepository;
    private final MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;
    private final UserLockPort userLockPort;
    private final UserQueryPort userQueryPort;
    private final SessionService sessionService;
    private final ApplicationEventPublisher eventPublisher;
    private final BookingResponseMapper bookingResponseMapper;
    private final GoogleCalendarConnectionPort googleCalendarConnectionPort;
    private final MeetingProviderFactory meetingProviderFactory;
    private BookingChatPort bookingChatPort;

    private TimeProvider timeProvider = TimeProvider.from(Clock.systemUTC());

    @Autowired(required = false)
    public void setTimeProvider(TimeProvider timeProvider) {
        if (timeProvider != null) {
            this.timeProvider = timeProvider;
        }
    }

    @Autowired(required = false)
    void setBookingChatPort(BookingChatPort bookingChatPort) {
        this.bookingChatPort = bookingChatPort;
    }

    @Transactional
    public BookingResponse acceptBooking(UUID mentorUserId, UUID bookingId, AcceptBookingRequest request) {
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }

        Booking booking = getBookingForMentorDecision(mentorUserId, bookingId);
        UUID slotId = booking.getSlot() == null ? null : booking.getSlot().getId();
        UUID menteeId = booking.getMenteeUserId() == null ? null : booking.getMenteeUserId();
        if (slotId == null || menteeId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Booking chưa gắn đầy đủ mentee và khung giờ mentoring");
        }

        List<UUID> participantIds = Stream.of(mentorUserId, menteeId)
                .distinct()
                .sorted()
                .toList();
        if (userLockPort.lockUsersForUpdate(participantIds).size() != participantIds.size()) {
            throw new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng tham gia booking");
        }

        MentorAvailabilitySlot slot = mentorAvailabilitySlotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy khung giờ mentoring"));

        if (booking.getSlot() == null || !booking.getSlot().getId().equals(slotId)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Khung giờ của booking đã thay đổi");
        }
        if ((booking.getStatus() == BookingStatus.ACCEPTED_AWAITING_PAYMENT
                || BookingActionPolicy.isScheduled(booking.getStatus()))
                && booking.getAcceptedAt() != null) {
            return bookingResponseMapper.toBookingResponse(booking);
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể chấp nhận booking đang chờ phản hồi");
        }
        if (!slot.isActive()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Khung giờ này hiện không còn khả dụng");
        }

        Instant nowUtc = timeProvider.instant();
        LocalDateTime nowBusiness = timeProvider.nowBusiness();
        Instant pendingExpireAtUtc = booking.getPendingExpireAtUtc() != null ? booking.getPendingExpireAtUtc()
                : (booking.getPendingExpireAt() != null ? BookingTime.toInstant(booking.getPendingExpireAt()) : null);
        if (pendingExpireAtUtc != null && !pendingExpireAtUtc.isAfter(nowUtc)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT,
                    "Yêu cầu đặt lịch đã quá hạn phản hồi và không thể được chấp nhận.");
        }
        Instant selectedStartAt = BookingTime.resolveSelectedStartUtc(booking);
        Instant selectedEndAt = BookingTime.resolveSelectedEndUtc(booking);
        if (selectedStartAt == null || selectedEndAt == null || !selectedEndAt.isAfter(selectedStartAt)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Booking đang thiếu selected segment hợp lệ");
        }
        if (bookingRepository.existsOverlappingBySlotIdAndStatusInUtc(
                slot.getId(),
                SLOT_LOCKING_STATUSES,
                selectedStartAt,
                selectedEndAt
        )) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Segment này đã được chấp nhận cho booking khác");
        }

        List<Booking> menteeOverlappingBookings = bookingRepository.findMenteeOverlappingBookingsForUpdateUtc(
                booking.getMenteeUserId(),
                SLOT_LOCKING_STATUSES,
                selectedStartAt,
                selectedEndAt
        );
        if (!menteeOverlappingBookings.isEmpty()) {
            BookingTransitionExecutor.apply(booking, BookingTransitionCommand.SYSTEM_REJECT, nowUtc);
            booking.setRejectReason("Mentee đã có lịch học khác trùng thời gian này");
            bookingRepository.save(booking);
            return bookingResponseMapper.toBookingResponse(booking);
        }

        List<Booking> pendingBookings = bookingRepository.findOverlappingBySlotIdAndStatusForUpdateUtc(
                slot.getId(),
                BookingStatus.PENDING,
                selectedStartAt,
                selectedEndAt
        );
        List<Booking> menteePendingBookings = bookingRepository.findMenteeOverlappingBookingsForUpdateUtc(
                booking.getMenteeUserId(),
                List.of(BookingStatus.PENDING),
                selectedStartAt,
                selectedEndAt
        );

        boolean hasActiveCalendar = googleCalendarConnectionPort != null
                && googleCalendarConnectionPort.hasActiveConnection(mentorUserId);

        MeetingPlatform selectedPlatform = request == null ? null : request.meetingPlatform();
        String selectedMeetingLink = trimToNull(request == null ? null : request.meetingLink());
        String selectedLocation = trimToNull(request == null ? null : request.location());

        if (!hasActiveCalendar) {
            if (selectedPlatform == null) {
                throw new BaseException(ErrorCode.BAD_REQUEST,
                        "Vui lòng chọn nền tảng phòng học (Google Meet, Zoom, Discord, MS Teams, Offline, Khác)");
            }
            if (selectedPlatform != MeetingPlatform.OFFLINE && !StringUtils.hasText(selectedMeetingLink)) {
                throw new BaseException(ErrorCode.BAD_REQUEST,
                        "Link phòng học là bắt buộc khi mentor chưa kết nối Google Calendar");
            }
            if (selectedPlatform == MeetingPlatform.OFFLINE && !StringUtils.hasText(selectedLocation) && !StringUtils.hasText(selectedMeetingLink)) {
                throw new BaseException(ErrorCode.BAD_REQUEST,
                        "Địa điểm hoặc thông tin gặp mặt là bắt buộc đối với hình thức Offline");
            }
        }

        if (selectedMeetingLink != null && selectedPlatform != null && meetingProviderFactory != null) {
            meetingProviderFactory.getProvider(selectedPlatform).validateMeetingLink(selectedMeetingLink);
        }

        boolean isFree = Boolean.TRUE.equals(booking.getServiceIsFreeSnapshot())
                || (booking.getServicePriceScoinSnapshot() != null && booking.getServicePriceScoinSnapshot() == 0);

        BookingTransitionExecutor.apply(booking,
                isFree ? BookingTransitionCommand.ACCEPT_FREE : BookingTransitionCommand.ACCEPT_PAID, nowUtc);
        booking.setMentorResponseNote(trimToNull(request == null ? null : request.mentorResponseNote()));
        booking.setLocation(selectedLocation);
        booking.setMeetingPlatform(selectedPlatform);
        booking.setMeetingLink(selectedMeetingLink);
        slot.setBooked(true);

        for (Booking pendingBooking : pendingBookings) {
            if (pendingBooking.getId().equals(booking.getId())) {
                continue;
            }
            BookingTransitionExecutor.apply(pendingBooking, BookingTransitionCommand.SYSTEM_REJECT, nowUtc);
            pendingBooking.setRejectReason(BookingQueueConstants.AUTO_REJECT_SLOT_ACCEPTED_REASON);
        }

        for (Booking pendingBooking : menteePendingBookings) {
            if (pendingBooking.getId().equals(booking.getId())) {
                continue;
            }
            BookingTransitionExecutor.apply(pendingBooking, BookingTransitionCommand.SYSTEM_REJECT, nowUtc);
            pendingBooking.setRejectReason("MENTEE_TIME_LOCKED_BY_OTHER_BOOKING");
        }

        bookingRepository.saveAll(new ArrayList<>(Stream.concat(pendingBookings.stream(), menteePendingBookings.stream())
                .collect(Collectors.toMap(Booking::getId, value -> value, (left, right) -> left))
                .values()));
        Booking savedBooking = bookingRepository.save(booking);

        UserSummaryRecord mentorSummary = userQueryPort.findUserSummaryById(mentorUserId).orElse(null);
        String mentorName = mentorSummary == null ? "Mentor" : mentorSummary.fullName();
        String mentorEmail = mentorSummary == null ? null : mentorSummary.email();

        if (isFree) {
            if (sessionService != null) {
                sessionService.createForAcceptedBooking(savedBooking);
            }
            if (bookingChatPort != null) {
                bookingChatPort.createDirectForAcceptedBooking(
                        savedBooking.getId(), savedBooking.getMentorUserId(), savedBooking.getMenteeUserId());
            }

            eventPublisher.publishEvent(new NotificationEvent(
                    savedBooking.getMenteeUserId(),
                    NotificationType.BOOKING_ACCEPTED,
                    "Mentor đã chấp nhận yêu cầu học miễn phí",
                    mentorName + " đã chấp nhận lịch mentoring miễn phí của bạn. Buổi học đã được xác nhận.",
                    "BOOKING",
                    savedBooking.getId()
            ));

            eventPublisher.publishEvent(BookingEmailNotificationEvent.builder()
                    .bookingId(savedBooking.getId())
                    .eventType(BookingEmailNotificationEvent.EventType.BOOKING_ACCEPTED_EMAIL)
                    .recipientEmail(menteeEmail(savedBooking))
                    .recipientName(menteeName(savedBooking))
                    .actorName(mentorName)
                    .bookingStartTime(savedBooking.getSelectedStartTime())
                    .bookingEndTime(savedBooking.getSelectedEndTime())
                    .learningGoalTitle(savedBooking.getLearningGoalTitle())
                    .learningGoalDescription(savedBooking.getLearningGoalDescription())
                    .serviceTitle(savedBooking.getServiceTitleSnapshot())
                    .serviceDurationMinutes(savedBooking.getServiceDurationSnapshot())
                    .serviceFree(savedBooking.getServiceIsFreeSnapshot())
                    .servicePriceScoin(savedBooking.getServicePriceScoinSnapshot())
                    .serviceExpectedOutcome(savedBooking.getServiceExpectedOutcomeSnapshot())
                    .mentorResponseNote(savedBooking.getMentorResponseNote())
                    .meetingLink(selectedMeetingLink)
                    .createdAt(timeProvider.nowBusiness())
                    .build());

            if (mentorEmail != null) {
                eventPublisher.publishEvent(BookingEmailNotificationEvent.builder()
                        .bookingId(savedBooking.getId())
                        .eventType(BookingEmailNotificationEvent.EventType.BOOKING_PAID_CONFIRMED_EMAIL)
                        .recipientEmail(mentorEmail)
                        .recipientName(mentorName)
                        .actorName(menteeName(savedBooking))
                        .bookingStartTime(savedBooking.getSelectedStartTime())
                        .bookingEndTime(savedBooking.getSelectedEndTime())
                        .learningGoalTitle(savedBooking.getLearningGoalTitle())
                        .learningGoalDescription(savedBooking.getLearningGoalDescription())
                        .serviceTitle(savedBooking.getServiceTitleSnapshot())
                        .serviceDurationMinutes(savedBooking.getServiceDurationSnapshot())
                        .serviceFree(savedBooking.getServiceIsFreeSnapshot())
                        .servicePriceScoin(savedBooking.getServicePriceScoinSnapshot())
                        .serviceExpectedOutcome(savedBooking.getServiceExpectedOutcomeSnapshot())
                        .mentorResponseNote(savedBooking.getMentorResponseNote())
                        .meetingLink(selectedMeetingLink)
                        .createdAt(timeProvider.nowBusiness())
                        .build());
            }
        } else {
            eventPublisher.publishEvent(BookingEmailNotificationEvent.builder()
                    .bookingId(savedBooking.getId())
                    .eventType(BookingEmailNotificationEvent.EventType.BOOKING_ACCEPTED_EMAIL)
                    .recipientEmail(menteeEmail(savedBooking))
                    .recipientName(menteeName(savedBooking))
                    .actorName(mentorName)
                    .bookingStartTime(savedBooking.getSelectedStartTime())
                    .bookingEndTime(savedBooking.getSelectedEndTime())
                    .learningGoalTitle(savedBooking.getLearningGoalTitle())
                    .learningGoalDescription(savedBooking.getLearningGoalDescription())
                    .serviceTitle(savedBooking.getServiceTitleSnapshot())
                    .serviceDurationMinutes(savedBooking.getServiceDurationSnapshot())
                    .serviceFree(savedBooking.getServiceIsFreeSnapshot())
                    .servicePriceScoin(savedBooking.getServicePriceScoinSnapshot())
                    .serviceExpectedOutcome(savedBooking.getServiceExpectedOutcomeSnapshot())
                    .mentorResponseNote(savedBooking.getMentorResponseNote())
                    .paymentDeadline(resolvePaymentDeadline(savedBooking))
                    .meetingLink(selectedMeetingLink)
                    .createdAt(timeProvider.nowBusiness())
                    .build());

            eventPublisher.publishEvent(new NotificationEvent(
                    savedBooking.getMenteeUserId(),
                    NotificationType.BOOKING_ACCEPTED,
                    "Mentor đã chấp nhận yêu cầu",
                    mentorName + " đã chấp nhận lịch mentoring của bạn. Vui lòng thanh toán để xác nhận buổi học.",
                    "BOOKING",
                    savedBooking.getId()
            ));
        }

        eventPublisher.publishEvent(new BookingStatusUpdatedEvent(
                savedBooking.getId(),
                savedBooking.getMenteeUserId(),
                savedBooking.getMentorUserId(),
                savedBooking.getStatus(),
                isFree ? "Mentor đã chấp nhận yêu cầu học miễn phí. Buổi học đã được xác nhận."
                       : "Mentor đã chấp nhận yêu cầu và đang chờ bạn thanh toán.",
                savedBooking.getUpdatedAt() != null ? savedBooking.getUpdatedAt() : timeProvider.nowBusiness()
        ));
        if (isFree && hasActiveCalendar) {
            eventPublisher.publishEvent(BookingCalendarLifecycleEvent.of(savedBooking.getId(), savedBooking.getMentorUserId(), BookingCalendarLifecycleEvent.Action.CREATE));
        }

        for (Booking pendingBooking : pendingBookings) {
            if (!pendingBooking.getId().equals(booking.getId())) {
                eventPublisher.publishEvent(new NotificationEvent(
                        pendingBooking.getMenteeUserId(),
                        NotificationType.BOOKING_AUTO_REJECTED,
                        "Yêu cầu đặt lịch đã bị hủy tự động",
                        "Khung giờ bạn chọn đã được mentor xác nhận cho một học viên khác.",
                        "BOOKING",
                        pendingBooking.getId()
                ));
                eventPublisher.publishEvent(BookingEmailNotificationEvent.builder()
                        .bookingId(pendingBooking.getId())
                        .eventType(BookingEmailNotificationEvent.EventType.BOOKING_REJECTED_EMAIL)
                        .recipientEmail(menteeEmail(pendingBooking))
                        .recipientName(menteeName(pendingBooking))
                        .actorName(mentorName)
                        .bookingStartTime(pendingBooking.getSelectedStartTime())
                        .bookingEndTime(pendingBooking.getSelectedEndTime())
                        .learningGoalTitle(pendingBooking.getLearningGoalTitle())
                        .learningGoalDescription(pendingBooking.getLearningGoalDescription())
                        .serviceTitle(pendingBooking.getServiceTitleSnapshot())
                        .serviceDurationMinutes(pendingBooking.getServiceDurationSnapshot())
                        .serviceFree(pendingBooking.getServiceIsFreeSnapshot())
                        .servicePriceScoin(pendingBooking.getServicePriceScoinSnapshot())
                        .serviceExpectedOutcome(pendingBooking.getServiceExpectedOutcomeSnapshot())
                        .createdAt(timeProvider.nowBusiness())
                        .build());
                eventPublisher.publishEvent(new BookingStatusUpdatedEvent(
                        pendingBooking.getId(),
                        pendingBooking.getMenteeUserId(),
                        pendingBooking.getMentorUserId(),
                        BookingStatus.REJECTED,
                        "Yêu cầu đặt lịch không còn khả dụng.",
                        pendingBooking.getUpdatedAt() != null ? pendingBooking.getUpdatedAt() : timeProvider.nowBusiness()
                ));
            }
        }

        return bookingResponseMapper.toBookingResponse(savedBooking);
    }

    @Transactional
    public BookingResponse rejectBooking(UUID mentorUserId, UUID bookingId, RejectBookingRequest request) {
        Booking booking = getBookingForMentorDecision(mentorUserId, bookingId);
        if (booking.getStatus() == BookingStatus.REJECTED && booking.getRejectedAt() != null) {
            return bookingResponseMapper.toBookingResponse(booking);
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể từ chối booking đang chờ phản hồi");
        }

        Instant nowUtc = timeProvider.instant();
        LocalDateTime nowBusiness = timeProvider.nowBusiness();
        BookingTransitionExecutor.apply(booking, BookingTransitionCommand.REJECT, nowUtc);
        booking.setRejectReason(trim(request.rejectReason()));
        booking.setMentorResponseNote(trimToNull(request.mentorResponseNote()));

        Booking savedBooking = bookingRepository.save(booking);

        UserSummaryRecord mentorSummary = userQueryPort.findUserSummaryById(mentorUserId).orElse(null);
        String mentorName = mentorSummary == null ? "Mentor" : mentorSummary.fullName();

        eventPublisher.publishEvent(new NotificationEvent(
                savedBooking.getMenteeUserId(),
                NotificationType.BOOKING_REJECTED,
                "Yêu cầu đặt lịch đã bị từ chối",
                mentorName + " đã từ chối yêu cầu đặt lịch của bạn.",
                "BOOKING",
                savedBooking.getId()
        ));

        eventPublisher.publishEvent(BookingEmailNotificationEvent.builder()
                .bookingId(savedBooking.getId())
                .eventType(BookingEmailNotificationEvent.EventType.BOOKING_REJECTED_EMAIL)
                .recipientEmail(menteeEmail(savedBooking))
                .recipientName(menteeName(savedBooking))
                .actorName(mentorName)
                .reason(savedBooking.getRejectReason())
                .createdAt(nowBusiness)
                .build());

        eventPublisher.publishEvent(new BookingStatusUpdatedEvent(
                savedBooking.getId(),
                savedBooking.getMenteeUserId(),
                savedBooking.getMentorUserId(),
                savedBooking.getStatus(),
                "Yêu cầu đặt lịch đã bị từ chối.",
                savedBooking.getUpdatedAt() != null ? savedBooking.getUpdatedAt() : nowBusiness
        ));

        return bookingResponseMapper.toBookingResponse(savedBooking);
    }

    private Booking getBookingForMentorDecision(UUID mentorUserId, UUID bookingId) {
        if (mentorUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không được để trống");
        }
        Booking booking = bookingRepository.findByIdForMentorDecision(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
        if (booking.getMentorUserId() == null || !mentorUserId.equals(booking.getMentorUserId())) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền thao tác trên booking này");
        }
        return booking;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private UserSummaryRecord menteeSummary(Booking booking) {
        return booking == null || booking.getMenteeUserId() == null
                ? null : userQueryPort.findUserSummaryById(booking.getMenteeUserId()).orElse(null);
    }

    private String menteeEmail(Booking booking) {
        UserSummaryRecord mentee = menteeSummary(booking);
        return mentee == null ? null : mentee.email();
    }

    private String menteeName(Booking booking) {
        UserSummaryRecord mentee = menteeSummary(booking);
        return mentee == null ? "Mentee" : mentee.fullName();
    }
}
