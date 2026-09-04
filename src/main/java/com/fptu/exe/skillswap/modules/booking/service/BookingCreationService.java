package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.infrastructure.telemetry.InternalTelemetryService;
import com.fptu.exe.skillswap.modules.booking.constant.BookingQueueConstants;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTime;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.identity.port.UserLockPort;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.identity.port.UserSummaryRecord;
import com.fptu.exe.skillswap.modules.mentor.port.MentorBookingCapability;
import com.fptu.exe.skillswap.modules.mentor.port.MentorBookingPolicyQuery;
import com.fptu.exe.skillswap.modules.mentor.port.MentorBookingQueryPort;
import com.fptu.exe.skillswap.modules.mentor.port.ServiceSlotCandidate;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingCreationService {

    private final BookingRepository bookingRepository;
    private final MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;
    private final UserQueryPort userQueryPort;
    private final UserLockPort userLockPort;
    private final MentorBookingQueryPort mentorBookingQueryPort;
    private final BookingSlotValidator bookingSlotValidator;
    private final BookingEligibilityPolicy bookingEligibilityPolicy;
    private final ApplicationEventPublisher eventPublisher;
    private final InternalTelemetryService internalTelemetryService;
    private final BookingResponseMapper bookingResponseMapper;
    private final MentorBookingPolicyQuery mentorBookingPolicyQuery;

    private TimeProvider timeProvider = TimeProvider.from(Clock.systemUTC());

    @Autowired(required = false)
    public void setTimeProvider(TimeProvider timeProvider) {
        if (timeProvider != null) {
            this.timeProvider = timeProvider;
        }
    }

    @Transactional
    public BookingResponse createBooking(UUID menteeUserId, CreateBookingRequest request) {
        if (menteeUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu dữ liệu tạo booking");
        }

        var lockedMentees = userLockPort.lockUsersForUpdate(List.of(menteeUserId));
        if (lockedMentees.size() != 1) {
            throw new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng hiện tại");
        }
        UserSummaryRecord mentee = userQueryPort.findUserSummaryById(menteeUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng hiện tại"));
        bookingEligibilityPolicy.validateBookerEligibility(mentee);

        long menteePendingCount = bookingRepository.countByMenteeUserIdAndStatus(menteeUserId, BookingStatus.PENDING);
        if (menteePendingCount >= BookingQueueConstants.MAX_PENDING_BOOKINGS_PER_MENTEE) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT,
                    "Bạn đang có tối đa 5 yêu cầu đặt lịch đang chờ phản hồi. Vui lòng chờ mentor phản hồi hoặc hủy bớt yêu cầu đang chờ để đặt lịch mới.");
        }

        MentorAvailabilitySlot slot = mentorAvailabilitySlotRepository.findByIdForUpdate(request.slotId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy khung giờ mentoring"));

        UUID mentorUserId = slot.getMentorUserId();
        if (mentorUserId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Khung giờ hiện tại không gắn với mentor hợp lệ");
        }
        if (mentorUserId.equals(menteeUserId)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Bạn không thể tự tạo booking với chính mình");
        }

        UserSummaryRecord mentorUser = userQueryPort.findUserSummaryById(mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor hiện không còn hoạt động"));
        if (!mentorUser.isActive()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor hiện không còn hoạt động");
        }

        MentorBookingCapability capability = mentorBookingQueryPort.getBookingCapability(mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor hiện chưa sẵn sàng nhận booking"));

        if (!capability.isActiveMentor()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor hiện chưa sẵn sàng nhận booking");
        }
        if (!capability.available()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor hiện đang tạm dừng nhận mentee mới");
        }
        if (!bookingEligibilityPolicy.isDiscoverableMentorForBooking(capability)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor hiện chưa sẵn sàng hiển thị để nhận booking");
        }

        Instant nowUtc = timeProvider.instant();
        LocalDateTime nowBusiness = timeProvider.nowBusiness();
        if (capability.bookingSuspendedUntil() != null && capability.bookingSuspendedUntil().isAfter(nowBusiness)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor đang bị tạm khóa nhận lịch mới đến " + capability.bookingSuspendedUntil());
        }
        if (!slot.isActive()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Khung giờ này hiện không còn khả dụng");
        }
        Instant slotStartUtc = slot.getStartTimeUtc() != null ? slot.getStartTimeUtc() : BookingTime.toInstant(slot.getStartTime());
        Instant slotEndUtc = slot.getEndTimeUtc() != null ? slot.getEndTimeUtc() : BookingTime.toInstant(slot.getEndTime());
        if (slotStartUtc == null || slotEndUtc == null || !slotEndUtc.isAfter(slotStartUtc)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Khung giờ mentoring hiện tại không hợp lệ");
        }
        if (!slotEndUtc.isAfter(nowUtc)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Khung giờ này đã kết thúc hoặc đã trôi qua");
        }

        ServiceSlotCandidate serviceCandidate = resolveServiceCandidate(request.serviceId(), mentorUserId);

        Instant requestedStartAt = request.startAt();
        if (requestedStartAt == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thời gian bắt đầu không được để trống");
        }
        requestedStartAt = requestedStartAt.truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        Instant requestedEndAt = requestedStartAt.plus(Duration.ofMinutes(serviceCandidate.durationMinutes()));
        LocalDateTime selectedStartTime = BookingTime.fromInstant(requestedStartAt);
        LocalDateTime selectedEndTime = BookingTime.fromInstant(requestedEndAt);

        bookingSlotValidator.validateSelectedRange(slot, serviceCandidate, requestedStartAt, requestedEndAt, nowUtc);
        bookingSlotValidator.validateServiceAttachedToSlot(slot.getId(), serviceCandidate.serviceId());
        bookingSlotValidator.validateCandidateSelection(slot, serviceCandidate, menteeUserId, requestedStartAt, requestedEndAt);
        if (mentorBookingPolicyQuery != null) {
            mentorBookingPolicyQuery.validateBookingWindow(mentorUserId, selectedStartTime, nowBusiness);
        }

        if (bookingRepository.existsByMenteeUserIdAndSlotIdAndSelectedStartTimeUtcAndSelectedEndTimeUtcAndStatusIn(
                menteeUserId,
                slot.getId(),
                requestedStartAt,
                requestedEndAt,
                List.of(BookingStatus.PENDING, BookingStatus.ACCEPTED_AWAITING_PAYMENT, BookingStatus.PAID)
        )) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT,
                    "Bạn đã có yêu cầu booking đang chờ hoặc đã được chấp nhận cho đúng segment này.");
        }

        Instant pendingExpireAtUtc = BookingDeadlinePolicy.resolvePendingExpiry(nowUtc, requestedStartAt);
        if (pendingExpireAtUtc == null || !pendingExpireAtUtc.isAfter(nowUtc)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT,
                    "Khung giờ không còn đủ thời gian để mentor phản hồi yêu cầu booking.");
        }
        LocalDateTime pendingExpireAt = BookingTime.fromInstant(pendingExpireAtUtc);

        Booking savedBooking = bookingRepository.save(Booking.builder()
                .menteeUserId(mentee.userId())
                .mentorUserId(mentorUserId)
                .serviceId(serviceCandidate.serviceId())
                .slot(slot)
                .learningGoalTitle(trim(request.learningGoalTitle()))
                .learningGoalDescription(trimToNull(request.learningGoalDescription()))
                .selectedStartTime(selectedStartTime)
                .selectedStartTimeUtc(requestedStartAt)
                .selectedEndTime(selectedEndTime)
                .selectedEndTimeUtc(requestedEndAt)
                .pendingExpireAt(pendingExpireAt)
                .pendingExpireAtUtc(pendingExpireAtUtc)
                .serviceTitleSnapshot(serviceCandidate.title())
                .serviceDescriptionSnapshot(serviceCandidate.description())
                .serviceDurationSnapshot(serviceCandidate.durationMinutes())
                .serviceExpectedOutcomeSnapshot(serviceCandidate.expectedOutcome())
                .serviceIsFreeSnapshot(serviceCandidate.isFree())
                .servicePriceScoinSnapshot(normalizedServicePrice(serviceCandidate))
                .maintainPostSessionChatSnapshot(serviceCandidate.maintainPostSessionChat())
                .build());

        eventPublisher.publishEvent(new NotificationEvent(
                mentorUserId,
                NotificationType.BOOKING_REQUEST_CREATED,
                "Bạn có yêu cầu đặt lịch mới",
                mentee.fullName() + " đã gửi yêu cầu đặt lịch mentoring.",
                "BOOKING",
                savedBooking.getId()
        ));

        eventPublisher.publishEvent(new BookingStatusUpdatedEvent(
                savedBooking.getId(),
                savedBooking.getMenteeUserId(),
                savedBooking.getMentorUserId(),
                savedBooking.getStatus(),
                "Yêu cầu đặt lịch mới đã được gửi.",
                savedBooking.getUpdatedAt() != null ? savedBooking.getUpdatedAt() : nowBusiness
        ));
        if (internalTelemetryService != null) {
            internalTelemetryService.record(
                    "BOOKING_CREATED",
                    menteeUserId,
                    "BOOKING",
                    savedBooking.getId(),
                    Map.of(
                            "mentorUserId", String.valueOf(savedBooking.getMentorUserId()),
                            "serviceId", String.valueOf(serviceCandidate.serviceId()),
                            "slotId", String.valueOf(slot.getId())
                    )
            );
        }

        return bookingResponseMapper.toBookingResponse(savedBooking);
    }

    private ServiceSlotCandidate resolveServiceCandidate(UUID serviceId, UUID mentorUserId) {
        if (serviceId == null) {
            return null;
        }
        ServiceSlotCandidate candidate = mentorBookingQueryPort.getActiveServiceCandidate(serviceId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.BAD_REQUEST, "Gói mentoring đã chọn không tồn tại hoặc không thuộc mentor này"));
        if (candidate.durationMinutes() == null || candidate.durationMinutes() <= 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Gói mentoring đã chọn có thời lượng không hợp lệ");
        }
        return candidate;
    }

    private Integer normalizedServicePrice(ServiceSlotCandidate service) {
        if (service == null || Boolean.TRUE.equals(service.isFree())) {
            return 0;
        }
        return service.priceScoin() == null ? 0 : service.priceScoin();
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
}
