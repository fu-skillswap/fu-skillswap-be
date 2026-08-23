package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.constant.BookingQueueConstants;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.port.MentorQueryPort;
import com.fptu.exe.skillswap.modules.mentor.service.MentorBookingPolicyService;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.event.NotificationEvent;
import com.fptu.exe.skillswap.modules.system.service.InternalTelemetryService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingCreationService {

    private final BookingRepository bookingRepository;
    private final MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;
    private final UserQueryPort userQueryPort;
    private final MentorQueryPort mentorQueryPort;
    private final BookingSlotValidator bookingSlotValidator;
    private final BookingEligibilityPolicy bookingEligibilityPolicy;
    private final ApplicationEventPublisher eventPublisher;
    private final InternalTelemetryService internalTelemetryService;
    private final BookingResponseMapper bookingResponseMapper;
    private final MentorBookingPolicyService mentorBookingPolicyService;

    @Transactional
    public BookingResponse createBooking(UUID menteeUserId, CreateBookingRequest request) {
        if (menteeUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu dữ liệu tạo booking");
        }

        User mentee = userQueryPort.findUserById(menteeUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng hiện tại"));
        bookingEligibilityPolicy.validateBookerEligibility(mentee);

        long menteePendingCount = bookingRepository.countByMenteeIdAndStatus(menteeUserId, BookingStatus.PENDING);
        if (menteePendingCount >= BookingQueueConstants.MAX_PENDING_BOOKINGS_PER_MENTEE) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT,
                    "Bạn đang có tối đa 5 yêu cầu đặt lịch đang chờ phản hồi. Vui lòng chờ mentor phản hồi hoặc hủy bớt yêu cầu đang chờ để đặt lịch mới.");
        }

        MentorAvailabilitySlot slot = mentorAvailabilitySlotRepository.findByIdForUpdate(request.slotId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy khung giờ mentoring"));

        MentorProfile mentorProfile = slot.getMentorProfile();
        if (mentorProfile == null || mentorProfile.getUser() == null || mentorProfile.getUserId() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Khung giờ hiện tại không gắn với mentor hợp lệ");
        }
        if (mentorProfile.getUserId().equals(menteeUserId)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Bạn không thể tự tạo booking với chính mình");
        }
        if (mentorProfile.getUser() == null || mentorProfile.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor hiện không còn hoạt động");
        }
        if (mentorProfile.getStatus() != MentorStatus.ACTIVE || mentorProfile.getVerifiedAt() == null) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor hiện chưa sẵn sàng nhận booking");
        }
        if (!mentorProfile.isAvailable()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor hiện đang tạm dừng nhận mentee mới");
        }
        if (!bookingEligibilityPolicy.isDiscoverableMentorForBooking(mentorProfile)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor hiện chưa sẵn sàng hiển thị để nhận booking");
        }
        LocalDateTime now = DateTimeUtil.now();
        if (mentorProfile.getBookingSuspendedUntil() != null && mentorProfile.getBookingSuspendedUntil().isAfter(now)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor đang bị tạm khóa nhận lịch mới đến " + mentorProfile.getBookingSuspendedUntil());
        }
        if (!slot.isActive()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Khung giờ này hiện không còn khả dụng");
        }
        if (slot.getStartTime() == null || slot.getEndTime() == null || !slot.getEndTime().isAfter(slot.getStartTime())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Khung giờ mentoring hiện tại không hợp lệ");
        }
        if (!slot.getEndTime().isAfter(now)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Khung giờ này đã kết thúc hoặc đã trôi qua");
        }

        MentorService mentorService = resolveMentorService(request.serviceId(), mentorProfile.getUserId());

        Instant requestedStartAt = request.startAt();
        if (requestedStartAt == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thời gian bắt đầu không được để trống");
        }
        requestedStartAt = requestedStartAt.truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        LocalDateTime selectedStartTime = LocalDateTime.ofInstant(requestedStartAt, ZoneOffset.UTC);
        LocalDateTime selectedEndTime = selectedStartTime.plusMinutes(mentorService.getDurationMinutes());
        bookingSlotValidator.validateSelectedRange(slot, mentorService, selectedStartTime, selectedEndTime, now);
        bookingSlotValidator.validateServiceAttachedToSlot(slot.getId(), mentorService.getId());
        bookingSlotValidator.validateCandidateSelection(slot, mentorService, menteeUserId, selectedStartTime, selectedEndTime);
        if (mentorBookingPolicyService != null) {
            mentorBookingPolicyService.validateBookingWindow(mentorProfile.getUserId(), selectedStartTime, now);
        }

        if (bookingRepository.existsByMenteeIdAndSlotIdAndSelectedStartTimeAndSelectedEndTimeAndStatusIn(
                menteeUserId,
                slot.getId(),
                selectedStartTime,
                selectedEndTime,
                List.of(BookingStatus.PENDING, BookingStatus.ACCEPTED_AWAITING_PAYMENT, BookingStatus.ACCEPTED, BookingStatus.PAID)
        )) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT,
                    "Bạn đã có yêu cầu booking đang chờ hoặc đã được chấp nhận cho đúng segment này.");
        }

        LocalDateTime pendingExpireAt = BookingDeadlinePolicy.resolvePendingExpiry(now, selectedStartTime);
        if (pendingExpireAt == null || !pendingExpireAt.isAfter(now)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT,
                    "Khung giờ không còn đủ thời gian để mentor phản hồi yêu cầu booking.");
        }

        Booking savedBooking = bookingRepository.save(Booking.builder()
                .mentee(mentee)
                .mentorProfile(mentorProfile)
                .service(mentorService)
                .slot(slot)
                .learningGoalTitle(trim(request.learningGoalTitle()))
                .learningGoalDescription(trimToNull(request.learningGoalDescription()))
                .selectedStartTime(selectedStartTime)
                .selectedEndTime(selectedEndTime)
                .pendingExpireAt(pendingExpireAt)
                .serviceTitleSnapshot(mentorService.getTitle())
                .serviceDescriptionSnapshot(mentorService.getDescription())
                .serviceDurationSnapshot(mentorService.getDurationMinutes())
                .serviceExpectedOutcomeSnapshot(mentorService.getExpectedOutcome())
                .serviceIsFreeSnapshot(mentorService.isFree())
                .servicePriceScoinSnapshot(normalizedServicePrice(mentorService))
                .maintainPostSessionChatSnapshot(mentorService.isMaintainPostSessionChat())
                .build());

        eventPublisher.publishEvent(new NotificationEvent(
                mentorProfile.getUserId(),
                NotificationType.BOOKING_REQUEST_CREATED,
                "Bạn có yêu cầu đặt lịch mới",
                mentee.getFullName() + " đã gửi yêu cầu đặt lịch mentoring.",
                "BOOKING",
                savedBooking.getId()
        ));

        eventPublisher.publishEvent(new BookingStatusUpdatedEvent(
                savedBooking.getId(),
                savedBooking.getMentee().getId(),
                savedBooking.getMentorProfile().getUserId(),
                savedBooking.getStatus(),
                "Yêu cầu đặt lịch mới đã được gửi.",
                savedBooking.getUpdatedAt() != null ? savedBooking.getUpdatedAt() : DateTimeUtil.now()
        ));
        if (internalTelemetryService != null) {
            internalTelemetryService.record(
                    "BOOKING_CREATED",
                    menteeUserId,
                    "BOOKING",
                    savedBooking.getId(),
                    Map.of(
                            "mentorUserId", String.valueOf(savedBooking.getMentorProfile().getUserId()),
                            "serviceId", String.valueOf(mentorService.getId()),
                            "slotId", String.valueOf(slot.getId())
                    )
            );
        }

        return bookingResponseMapper.toBookingResponse(savedBooking);
    }

    private MentorService resolveMentorService(UUID serviceId, UUID mentorUserId) {
        if (serviceId == null) {
            return null;
        }
        if (mentorQueryPort == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Gói mentoring đã chọn không tồn tại hoặc không thuộc mentor này");
        }
        MentorService mentorService = mentorQueryPort.findActiveServiceByIdAndMentorUserId(serviceId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.BAD_REQUEST, "Gói mentoring đã chọn không tồn tại hoặc không thuộc mentor này"));
        if (mentorService.getDurationMinutes() == null || mentorService.getDurationMinutes() <= 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Gói mentoring đã chọn có thời lượng không hợp lệ");
        }
        return mentorService;
    }

    private Integer normalizedServicePrice(MentorService service) {
        if (service == null || service.isFree()) {
            return 0;
        }
        return service.getPriceScoin() == null ? 0 : service.getPriceScoin();
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
