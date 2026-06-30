package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.constant.BookingQueueConstants;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingCompletionOutcome;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueType;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MeetingPlatform;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingIssueResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.dto.request.AdminBookingListRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.AdminResolveBookingIssueRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.BookingListRequest;
import com.fptu.exe.skillswap.modules.booking.dto.BookingViewRole;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.AcceptBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.RejectBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.CancelBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.CompleteBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.ConfirmBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.SaveMeetingLinkRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.SubmitBookingIssueRequest;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
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

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingService {

    private static final long MENTEE_FREE_CANCEL_DEADLINE_MINUTES = 6 * 60;
    private static final long MENTOR_SAFE_CANCEL_DEADLINE_MINUTES = 12 * 60;
    private static final long MENTOR_SUSPENSION_CANCEL_DEADLINE_MINUTES = 6 * 60;
    private static final BigDecimal MENTOR_LATE_CANCEL_PENALTY = BigDecimal.valueOf(0.5);
    private static final int MENTOR_LATE_CANCEL_SUSPENSION_DAYS = 3;
    private static final long POST_SESSION_REVIEW_WINDOW_HOURS = 24;
    private static final long PAYMENT_WINDOW_MINUTES = 120;
    private static final List<BookingStatus> SLOT_LOCKING_STATUSES = List.of(
            BookingStatus.ACCEPTED_AWAITING_PAYMENT,
            BookingStatus.ACCEPTED,
            BookingStatus.PAID
    );
    private static final List<BookingStatus> BOOKING_LIST_PAID_PRIORITY_STATUSES = List.of(
            BookingStatus.PAID,
            BookingStatus.ACCEPTED
    );
    private static final List<BookingStatus> BOOKING_LIST_CANCELLED_PRIORITY_STATUSES = List.of(
            BookingStatus.CANCELLED_BY_MENTEE,
            BookingStatus.CANCELLED_BY_MENTOR
    );

    private final BookingRepository bookingRepository;
    private final MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;
    private final MentorServiceRepository mentorServiceRepository;
    private final UserRepository userRepository;
    private final com.fptu.exe.skillswap.modules.notification.service.NotificationService notificationService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository mentorProfileRepository;
    private final EntityManager entityManager;
    private final com.fptu.exe.skillswap.modules.session.service.SessionService sessionService;
    private final com.fptu.exe.skillswap.modules.conversation.service.ConversationService conversationService;
    private final com.fptu.exe.skillswap.modules.payment.service.SettlementService settlementService;
    private final com.fptu.exe.skillswap.modules.payment.service.PaymentOrderService paymentOrderService;
    private final BookingSlotValidator bookingSlotValidator;
    private final BookingEligibilityPolicy bookingEligibilityPolicy;

    @Transactional
    public BookingResponse createBooking(UUID menteeUserId, CreateBookingRequest request) {
        if (menteeUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu dữ liệu tạo booking");
        }

        User mentee = userRepository.findById(menteeUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng hiện tại"));
        bookingEligibilityPolicy.validateBookerEligibility(mentee);

        long menteePendingCount = bookingRepository.countByMenteeIdAndStatus(menteeUserId, BookingStatus.PENDING);
        if (menteePendingCount >= BookingQueueConstants.MAX_PENDING_BOOKINGS_PER_MENTEE) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT,
                    "Bạn đang có tối đa 5 yêu cầu đặt lịch đang chờ phản hồi. Vui lòng chờ mentor phản hồi hoặc hủy bớt yêu cầu đang chờ để đặt lịch mới.");
        }

        MentorAvailabilitySlot slot = mentorAvailabilitySlotRepository.findByIdForUpdate(request.availabilitySlotId())
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
        LocalDateTime selectedStartTime = request.selectedStartTime();
        LocalDateTime selectedEndTime = request.selectedEndTime();
        bookingSlotValidator.validateSelectedRange(slot, mentorService, selectedStartTime, selectedEndTime, now);
        bookingSlotValidator.validateServiceAttachedToSlot(slot.getId(), mentorService.getId());
        bookingSlotValidator.validateCandidateSelection(slot, mentorService, menteeUserId, selectedStartTime, selectedEndTime);

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

        Booking savedBooking = bookingRepository.save(Booking.builder()
                .mentee(mentee)
                .mentorProfile(mentorProfile)
                .service(mentorService)
                .slot(slot)
                .learningGoalTitle(trim(request.learningGoalTitle()))
                .learningGoalDescription(trimToNull(request.learningGoalDescription()))
                .selectedStartTime(selectedStartTime)
                .selectedEndTime(selectedEndTime)
                .serviceTitleSnapshot(mentorService.getTitle())
                .serviceDescriptionSnapshot(mentorService.getDescription())
                .serviceDurationSnapshot(mentorService.getDurationMinutes())
                .serviceExpectedOutcomeSnapshot(mentorService.getExpectedOutcome())
                .serviceIsFreeSnapshot(mentorService.isFree())
                .servicePriceScoinSnapshot(mentorService.getPriceScoin())
                .build());

        eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.notification.event.NotificationEvent(
                mentorProfile.getUserId(),
                com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_REQUEST_CREATED,
                "Bạn có yêu cầu đặt lịch mới",
                mentee.getFullName() + " đã gửi yêu cầu đặt lịch mentoring.",
                "BOOKING",
                savedBooking.getId()
        ));

        eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent(
                savedBooking.getId(),
                savedBooking.getMentee().getId(),
                savedBooking.getMentorProfile().getUserId(),
                savedBooking.getStatus(),
                "Yêu cầu đặt lịch mới đã được gửi.",
                savedBooking.getUpdatedAt() != null ? savedBooking.getUpdatedAt() : DateTimeUtil.now()
        ));

        return toBookingResponse(savedBooking);
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> getMyBookings(UUID currentUserId, BookingListRequest request) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }

        BookingListRequest safeRequest = request == null ? new BookingListRequest() : request;
        BookingViewRole role = safeRequest.getRole() == null ? BookingViewRole.MENTEE : safeRequest.getRole();
        Pageable pageable = safeRequest.getStatus() == null
                ? bookingPriorityPageable(safeRequest)
                : bookingPageable(safeRequest);

        LocalDateTime startTimeStart = DateTimeUtil.now().minusDays(7);
        LocalDateTime startTimeEnd = DateTimeUtil.now().plusDays(7);

        Page<Booking> page = switch (role) {
            case MENTEE -> safeRequest.getStatus() == null
                    ? bookingRepository.findMyMenteeBookingsOrderedByDashboardPriority(
                            currentUserId,
                            BOOKING_LIST_PAID_PRIORITY_STATUSES,
                            BookingStatus.ACCEPTED_AWAITING_PAYMENT,
                            BookingStatus.PENDING,
                            BOOKING_LIST_CANCELLED_PRIORITY_STATUSES,
                            startTimeStart,
                            startTimeEnd,
                            pageable
                    )
                    : bookingRepository.findMyMenteeBookingsByStatusAndDateRange(currentUserId, safeRequest.getStatus(), startTimeStart, startTimeEnd, pageable);
            case MENTOR -> safeRequest.getStatus() == null
                    ? bookingRepository.findMyMentorBookingsOrderedByDashboardPriority(
                            currentUserId,
                            BOOKING_LIST_PAID_PRIORITY_STATUSES,
                            BookingStatus.ACCEPTED_AWAITING_PAYMENT,
                            BookingStatus.PENDING,
                            BOOKING_LIST_CANCELLED_PRIORITY_STATUSES,
                            startTimeStart,
                            startTimeEnd,
                            pageable
                    )
                    : bookingRepository.findMyMentorBookingsByStatusAndDateRange(currentUserId, safeRequest.getStatus(), startTimeStart, startTimeEnd, pageable);
        };

        java.util.List<UUID> bookingIds = page.getContent().stream().map(Booking::getId).toList();
        java.util.Map<UUID, UUID> bookingToConvMap = conversationService != null
                ? conversationService.findConversationIdsByBookingIds(bookingIds)
                : java.util.Collections.emptyMap();

        return PageResponse.<BookingResponse>builder()
                .content(page.getContent().stream()
                        .map(b -> toBookingResponse(b, bookingToConvMap))
                        .toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingDetail(UUID currentUserId, UUID bookingId) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
        assertBookingAccess(booking, currentUserId);

        return toBookingResponse(booking);
    }

    @Transactional
    public BookingResponse acceptBooking(UUID mentorUserId, UUID bookingId, AcceptBookingRequest request) {
        if (mentorUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }

        // 1. Load the slot ID without loading the whole booking entity to prevent session caching issues
        UUID slotId = bookingRepository.findSlotIdByBookingId(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking hoặc booking không gắn với khung giờ mentoring"));

        // 2. Lock MentorAvailabilitySlot first
        MentorAvailabilitySlot slot = mentorAvailabilitySlotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy khung giờ mentoring"));

        // 3. Lock the target booking after slot lock
        Booking booking = getBookingForMentorDecision(mentorUserId, bookingId);

        // 4. Revalidate after locks are acquired
        if (booking.getSlot() == null || !booking.getSlot().getId().equals(slotId)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Khung giờ của booking đã thay đổi");
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể chấp nhận booking đang chờ phản hồi");
        }
        if (!slot.isActive()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Khung giờ này hiện không còn khả dụng");
        }

        LocalDateTime now = DateTimeUtil.now();
        LocalDateTime selectedStartTime = selectedStartTime(booking);
        LocalDateTime selectedEndTime = selectedEndTime(booking);
        if (selectedStartTime == null || selectedEndTime == null || !selectedEndTime.isAfter(selectedStartTime)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Booking đang thiếu selected segment hợp lệ");
        }
        if (bookingRepository.existsOverlappingBySlotIdAndStatusIn(
                slot.getId(),
                SLOT_LOCKING_STATUSES,
                selectedStartTime,
                selectedEndTime
        )) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Segment này đã được chấp nhận cho booking khác");
        }

        // 5. Lock sibling pending bookings overlap the same selected segment in deterministic order
        List<Booking> pendingBookings = bookingRepository.findOverlappingBySlotIdAndStatusForUpdate(
                slot.getId(),
                BookingStatus.PENDING,
                selectedStartTime,
                selectedEndTime
        );

        boolean isFree = Boolean.TRUE.equals(booking.getServiceIsFreeSnapshot())
                || (booking.getServicePriceScoinSnapshot() != null && booking.getServicePriceScoinSnapshot() == 0);

        if (isFree) {
            booking.setStatus(BookingStatus.PAID);
        } else {
            booking.setStatus(BookingStatus.ACCEPTED_AWAITING_PAYMENT);
        }
        booking.setAcceptedAt(now);
        booking.setMentorResponseNote(trimToNull(request == null ? null : request.mentorResponseNote()));
        slot.setBooked(true);

        for (Booking pendingBooking : pendingBookings) {
            if (pendingBooking.getId().equals(booking.getId())) {
                continue;
            }
            pendingBooking.setStatus(BookingStatus.REJECTED);
            pendingBooking.setRejectedAt(now);
            pendingBooking.setRejectReason(BookingQueueConstants.AUTO_REJECT_SLOT_ACCEPTED_REASON);
        }

        bookingRepository.saveAll(pendingBookings);
        Booking savedBooking = bookingRepository.save(booking);

        if (isFree) {
            sessionService.createForAcceptedBooking(savedBooking);
            conversationService.createDirectForAcceptedBooking(savedBooking);

            eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.notification.event.NotificationEvent(
                    savedBooking.getMentee().getId(),
                    com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_ACCEPTED,
                    "Mentor đã chấp nhận yêu cầu học miễn phí",
                    savedBooking.getMentorProfile().getUser().getFullName() + " đã chấp nhận lịch mentoring miễn phí của bạn. Buổi học đã được xác nhận.",
                    "BOOKING",
                    savedBooking.getId()
            ));

            eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.notification.event.NotificationEvent(
                    savedBooking.getMentorProfile().getUserId(),
                    com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_PAYMENT_CONFIRMED,
                    "Lịch học miễn phí của bạn đã được xác nhận",
                    "Bạn đã chấp nhận lịch học miễn phí với " + savedBooking.getMentee().getFullName() + ".",
                    "BOOKING",
                    savedBooking.getId()
            ));
        } else {
            eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.notification.event.NotificationEvent(
                    savedBooking.getMentee().getId(),
                    com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_ACCEPTED,
                    "Mentor đã chấp nhận yêu cầu và đang chờ bạn thanh toán",
                    savedBooking.getMentorProfile().getUser().getFullName() + " đã chấp nhận lịch mentoring của bạn. Vui lòng hoàn tất thanh toán trong vòng 2 giờ.",
                    "BOOKING",
                    savedBooking.getId()
            ));
        }

        for (Booking pendingBooking : pendingBookings) {
            if (!pendingBooking.getId().equals(booking.getId())) {
                eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.notification.event.NotificationEvent(
                        pendingBooking.getMentee().getId(),
                        com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_AUTO_REJECTED,
                        "Yêu cầu đặt lịch không còn khả dụng",
                        "Khung giờ này đã được mentor chấp nhận cho một yêu cầu khác.",
                        "BOOKING",
                        pendingBooking.getId()
                ));
            }
        }

        if (isFree) {
            // Email cho Mentee
            eventPublisher.publishEvent(com.fptu.exe.skillswap.modules.booking.event.BookingEmailNotificationEvent.builder()
                    .bookingId(savedBooking.getId())
                    .eventType(com.fptu.exe.skillswap.modules.booking.event.BookingEmailNotificationEvent.EventType.BOOKING_ACCEPTED_EMAIL)
                    .recipientEmail(savedBooking.getMentee().getEmail())
                    .recipientName(savedBooking.getMentee().getFullName())
                    .actorName(savedBooking.getMentorProfile().getUser().getFullName())
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
                    .createdAt(DateTimeUtil.now())
                    .build());

            // Email cho Mentor
            eventPublisher.publishEvent(com.fptu.exe.skillswap.modules.booking.event.BookingEmailNotificationEvent.builder()
                    .bookingId(savedBooking.getId())
                    .eventType(com.fptu.exe.skillswap.modules.booking.event.BookingEmailNotificationEvent.EventType.BOOKING_PAID_CONFIRMED_EMAIL)
                    .recipientEmail(savedBooking.getMentorProfile().getUser().getEmail())
                    .recipientName(savedBooking.getMentorProfile().getUser().getFullName())
                    .actorName(savedBooking.getMentee().getFullName())
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
                    .createdAt(DateTimeUtil.now())
                    .build());
        } else {
            eventPublisher.publishEvent(com.fptu.exe.skillswap.modules.booking.event.BookingEmailNotificationEvent.builder()
                    .bookingId(savedBooking.getId())
                    .eventType(com.fptu.exe.skillswap.modules.booking.event.BookingEmailNotificationEvent.EventType.BOOKING_ACCEPTED_EMAIL)
                    .recipientEmail(savedBooking.getMentee().getEmail())
                    .recipientName(savedBooking.getMentee().getFullName())
                    .actorName(savedBooking.getMentorProfile().getUser().getFullName())
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
                    .paymentDeadline(now.plusMinutes(PAYMENT_WINDOW_MINUTES))
                    .createdAt(DateTimeUtil.now())
                    .build());
        }

        eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent(
                savedBooking.getId(),
                savedBooking.getMentee().getId(),
                savedBooking.getMentorProfile().getUserId(),
                savedBooking.getStatus(),
                isFree ? "Mentor đã chấp nhận yêu cầu học miễn phí. Buổi học đã được xác nhận."
                       : "Mentor đã chấp nhận yêu cầu và đang chờ bạn thanh toán.",
                savedBooking.getUpdatedAt() != null ? savedBooking.getUpdatedAt() : DateTimeUtil.now()
        ));

        for (Booking pendingBooking : pendingBookings) {
            if (!pendingBooking.getId().equals(booking.getId())) {
                eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent(
                        pendingBooking.getId(),
                        pendingBooking.getMentee().getId(),
                        pendingBooking.getMentorProfile().getUserId(),
                        BookingStatus.REJECTED,
                        "Yêu cầu đặt lịch không còn khả dụng.",
                        pendingBooking.getUpdatedAt() != null ? pendingBooking.getUpdatedAt() : DateTimeUtil.now()
                ));
            }
        }

        return toBookingResponse(savedBooking);
    }

    @Transactional
    public BookingResponse rejectBooking(UUID mentorUserId, UUID bookingId, RejectBookingRequest request) {
        Booking booking = getBookingForMentorDecision(mentorUserId, bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể từ chối booking đang chờ phản hồi");
        }

        booking.setStatus(BookingStatus.REJECTED);
        booking.setRejectedAt(DateTimeUtil.now());
        booking.setRejectReason(trim(request.rejectReason()));
        booking.setMentorResponseNote(trimToNull(request.mentorResponseNote()));

        MentorProfile mentorProfile = booking.getMentorProfile();
        if (mentorProfile != null) {
            MentorProfile lockedProfile = mentorProfileRepository.findByIdForUpdate(mentorProfile.getUserId())
                    .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ mentor"));
            entityManager.refresh(lockedProfile);
            lockedProfile.setTotalRejectedBookings(defaultInteger(lockedProfile.getTotalRejectedBookings()) + 1);
            mentorProfileRepository.save(lockedProfile);
        }

        Booking savedBooking = bookingRepository.save(booking);

        eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.notification.event.NotificationEvent(
                savedBooking.getMentee().getId(),
                com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_REJECTED,
                "Yêu cầu đặt lịch đã bị từ chối",
                savedBooking.getMentorProfile().getUser().getFullName() + " đã từ chối yêu cầu đặt lịch của bạn.",
                "BOOKING",
                savedBooking.getId()
        ));

        eventPublisher.publishEvent(com.fptu.exe.skillswap.modules.booking.event.BookingEmailNotificationEvent.builder()
                .bookingId(savedBooking.getId())
                .eventType(com.fptu.exe.skillswap.modules.booking.event.BookingEmailNotificationEvent.EventType.BOOKING_REJECTED_EMAIL)
                .recipientEmail(savedBooking.getMentee().getEmail())
                .recipientName(savedBooking.getMentee().getFullName())
                .actorName(savedBooking.getMentorProfile().getUser().getFullName())
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
                .reason(savedBooking.getRejectReason())
                .createdAt(DateTimeUtil.now())
                .build());

        eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent(
                savedBooking.getId(),
                savedBooking.getMentee().getId(),
                savedBooking.getMentorProfile().getUserId(),
                savedBooking.getStatus(),
                "Yêu cầu đặt lịch đã bị từ chối.",
                savedBooking.getUpdatedAt() != null ? savedBooking.getUpdatedAt() : DateTimeUtil.now()
        ));

        return toBookingResponse(savedBooking);
    }

    @Transactional
    public BookingResponse saveMeetingLink(UUID mentorUserId, UUID bookingId, SaveMeetingLinkRequest request) {
        Booking booking = getBookingForMentorDecision(mentorUserId, bookingId);
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu dữ liệu meeting link");
        }
        if (!isConfirmedBookingStatus(booking.getStatus())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể cập nhật meeting link cho booking đã được xác nhận thanh toán");
        }

        com.fptu.exe.skillswap.modules.session.domain.Session session = sessionService.findByBookingId(bookingId);
        if (session == null) {
            session = sessionService.createForAcceptedBooking(booking);
        }
        MeetingPlatform previousPlatform = session.getMeetingPlatform();
        String previousMeetingLink = trimToNull(session.getMeetingLink());
        String previousLocation = trimToNull(booking.getLocation());
        MeetingPlatform nextPlatform = request.meetingPlatform();
        String nextMeetingLink = cleanMeetingLink(request.meetingLink());
        String nextLocation = trimToNull(request.location());
        boolean meetingChanged = !Objects.equals(previousPlatform, nextPlatform)
                || !Objects.equals(previousMeetingLink, nextMeetingLink)
                || !Objects.equals(previousLocation, nextLocation);

        session.setMeetingPlatform(nextPlatform);
        session.setMeetingLink(nextMeetingLink);
        booking.setLocation(nextLocation);

        Booking savedBooking = bookingRepository.save(booking);

        if (meetingChanged) {
            eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.notification.event.NotificationEvent(
                    savedBooking.getMentee().getId(),
                    com.fptu.exe.skillswap.modules.notification.domain.NotificationType.MEETING_LINK_UPDATED,
                    "Thông tin buổi học đã được cập nhật",
                    savedBooking.getMentorProfile().getUser().getFullName() + " đã cập nhật link hoặc địa điểm học.",
                    "BOOKING",
                    savedBooking.getId()
            ));
        }

        eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent(
                savedBooking.getId(),
                savedBooking.getMentee().getId(),
                savedBooking.getMentorProfile().getUserId(),
                savedBooking.getStatus(),
                "Thông tin phòng học đã được cập nhật.",
                savedBooking.getUpdatedAt() != null ? savedBooking.getUpdatedAt() : DateTimeUtil.now()
        ));

        return toBookingResponse(savedBooking);
    }

    @Transactional
    public BookingResponse completeBooking(UUID currentUserId, UUID bookingId, CompleteBookingRequest request) {
        Booking booking = getBookingForSessionAction(currentUserId, bookingId);
        return isMentorOfBooking(booking, currentUserId)
                ? completeBookingByMentor(currentUserId, bookingId, request)
                : confirmBookingByParticipant(currentUserId, bookingId, new ConfirmBookingRequest(
                        request == null ? null : request.completionNote()
                ));
    }

    @Transactional
    public BookingResponse completeBookingByMentor(UUID mentorUserId, UUID bookingId, CompleteBookingRequest request) {
        if (mentorUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }

        LocalDateTime now = DateTimeUtil.now();
        Booking booking = getBookingForSessionAction(mentorUserId, bookingId);
        if (!isMentorOfBooking(booking, mentorUserId)) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Chỉ mentor của booking mới được xác nhận hoàn tất buổi mentoring");
        }
        synchronizePostSessionStatusForPhaseOne(booking, now);
        if (booking.getStatus() != BookingStatus.AWAITING_MENTOR_COMPLETION) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking hiện chưa ở trạng thái chờ mentor hoàn tất");
        }
        if (selectedEndTime(booking) == null || now.isBefore(selectedEndTime(booking))) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chưa thể hoàn tất booking trước khi buổi mentoring kết thúc");
        }

        String completionNote = trimToNull(request == null ? null : request.completionNote());
        booking.setMentorNote(completionNote);
        booking.setStatus(BookingStatus.AWAITING_MENTEE_CONFIRMATION);
        booking.setCompletedAt(now);

        if (booking.getActualStartTime() == null) {
            booking.setActualStartTime(selectedStartTime(booking));
        }
        if (booking.getActualEndTime() == null) {
            booking.setActualEndTime(selectedEndTime(booking));
        }

        com.fptu.exe.skillswap.modules.session.domain.Session session = sessionService.findByBookingId(bookingId);
        if (session != null) {
            session.setStatus(com.fptu.exe.skillswap.modules.session.domain.SessionStatus.COMPLETED);
            if (session.getActualStartTime() == null) {
                session.setActualStartTime(selectedStartTime(booking));
            }
            if (session.getActualEndTime() == null) {
                session.setActualEndTime(selectedEndTime(booking));
            }
        }

        Booking savedBooking = bookingRepository.save(booking);
        settlementService.releaseForBooking(savedBooking);
        eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.notification.event.NotificationEvent(
                savedBooking.getMentee().getId(),
                com.fptu.exe.skillswap.modules.notification.domain.NotificationType.SESSION_COMPLETED,
                "Mentor đã xác nhận hoàn tất buổi mentoring",
                "Buổi mentoring đã chờ bạn xác nhận hoặc báo vấn đề trong 24 giờ.",
                "BOOKING",
                savedBooking.getId()
        ));

        eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent(
                savedBooking.getId(),
                savedBooking.getMentee().getId(),
                savedBooking.getMentorProfile().getUserId(),
                savedBooking.getStatus(),
                "Mentor đã xác nhận hoàn tất buổi học.",
                savedBooking.getUpdatedAt() != null ? savedBooking.getUpdatedAt() : DateTimeUtil.now()
        ));

        return toBookingResponse(savedBooking);
    }

    @Transactional
    public BookingResponse confirmBookingByParticipant(UUID currentUserId, UUID bookingId, ConfirmBookingRequest request) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }

        Booking booking = getBookingForSessionAction(currentUserId, bookingId);
        LocalDateTime now = DateTimeUtil.now();
        synchronizePostSessionStatusForPhaseOne(booking, now);
        assertBookingAccess(booking, currentUserId);

        if (booking.getStatus() != BookingStatus.AWAITING_MENTEE_CONFIRMATION) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking hiện chưa ở trạng thái chờ xác nhận sau buổi học");
        }
        if (isMentorOfBooking(booking, currentUserId)) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Mentor không thể tự xác nhận thay cho participant còn lại");
        }
        ensureWithinPostSessionReviewWindow(booking, now);

        booking.setStatus(BookingStatus.COMPLETED);
        booking.setFinalizedAt(now);
        booking.setCompletionOutcome(BookingCompletionOutcome.COMPLETED_CONFIRMED);
        booking.setCompletedAt(now);
        booking.setMenteeNote(trimToNull(request == null ? null : request.confirmationNote()));

        MentorProfile mentorProfile = booking.getMentorProfile();
        if (mentorProfile != null) {
            MentorProfile lockedProfile = mentorProfileRepository.findByIdForUpdate(mentorProfile.getUserId())
                    .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ mentor"));
            entityManager.refresh(lockedProfile);
            lockedProfile.setTotalCompletedSessions(defaultInteger(lockedProfile.getTotalCompletedSessions()) + 1);
            lockedProfile.setTotalSessions(defaultInteger(lockedProfile.getTotalSessions()) + 1);
            mentorProfileRepository.save(lockedProfile);
        }

        Booking savedBooking = bookingRepository.save(booking);
        eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent(
                savedBooking.getId(),
                savedBooking.getMentee().getId(),
                savedBooking.getMentorProfile().getUserId(),
                savedBooking.getStatus(),
                "Mentee xác nhận hoàn tất buổi học thành công.",
                savedBooking.getUpdatedAt() != null ? savedBooking.getUpdatedAt() : DateTimeUtil.now()
        ));
        return toBookingResponse(savedBooking);
    }

    @Transactional
    public BookingIssueResponse submitBookingIssue(UUID currentUserId, UUID bookingId, SubmitBookingIssueRequest request) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu dữ liệu báo vấn đề");
        }

        Booking booking = getBookingForSessionAction(currentUserId, bookingId);
        LocalDateTime now = DateTimeUtil.now();
        synchronizePostSessionStatusForPhaseOne(booking, now);
        assertBookingAccess(booking, currentUserId);

        if (booking.getStatus() != BookingStatus.AWAITING_MENTOR_COMPLETION
                && booking.getStatus() != BookingStatus.AWAITING_MENTEE_CONFIRMATION) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking hiện chưa ở trạng thái cho phép báo vấn đề");
        }
        if (selectedEndTime(booking) == null || now.isBefore(selectedEndTime(booking))) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể báo vấn đề sau khi buổi mentoring đã kết thúc");
        }
        ensureWithinPostSessionReviewWindow(booking, now);

        booking.setStatus(BookingStatus.UNDER_REVIEW);
        booking.setIssueSubmittedAt(now);
        booking.setIssueType(request.issueType());
        booking.setIssueDescription(trim(request.description()));
        booking.setWantsAdminReview(request.wantsAdminReview());
        booking.setCompletionOutcome(BookingCompletionOutcome.REVIEW_PENDING_DECISION);

        Booking savedBooking = bookingRepository.save(booking);
        eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent(
                savedBooking.getId(),
                savedBooking.getMentee().getId(),
                savedBooking.getMentorProfile().getUserId(),
                savedBooking.getStatus(),
                "Buổi học đã được báo cáo vấn đề và đang được xem xét.",
                savedBooking.getUpdatedAt() != null ? savedBooking.getUpdatedAt() : DateTimeUtil.now()
        ));
        return BookingIssueResponse.builder()
                .bookingId(savedBooking.getId())
                .status(savedBooking.getStatus())
                .issueSubmittedAt(savedBooking.getIssueSubmittedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> getAdminBookings(AdminBookingListRequest request) {
        AdminBookingListRequest safeRequest = request == null ? new AdminBookingListRequest() : request;
        Page<Booking> page = bookingRepository.searchForAdmin(
                safeRequest.getStatus(),
                safeRequest.getMentorUserId(),
                safeRequest.getMenteeUserId(),
                adminBookingPageable(safeRequest)
        );

        java.util.List<UUID> bookingIds = page.getContent().stream().map(Booking::getId).toList();
        java.util.Map<UUID, UUID> bookingToConvMap = conversationService != null
                ? conversationService.findConversationIdsByBookingIds(bookingIds)
                : java.util.Collections.emptyMap();

        return PageResponse.<BookingResponse>builder()
                .content(page.getContent().stream().map(b -> toBookingResponse(b, bookingToConvMap)).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Transactional
    public BookingResponse resolveBookingIssue(UUID adminUserId, UUID bookingId, AdminResolveBookingIssueRequest request) {
        if (adminUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu dữ liệu resolve booking issue");
        }

        Booking booking = bookingRepository.findByIdForSessionUpdate(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
        if (booking.getStatus() != BookingStatus.UNDER_REVIEW) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể resolve booking đang UNDER_REVIEW");
        }

        LocalDateTime now = DateTimeUtil.now();
        booking.setIssueResolvedAt(now);
        booking.setIssueResolvedByUserId(adminUserId);
        booking.setIssueResolutionNote(trimToNull(request.adminNote()));

        if (request.action() == com.fptu.exe.skillswap.modules.booking.domain.AdminBookingIssueResolutionAction.COMPLETE) {
            booking.setStatus(BookingStatus.COMPLETED);
            booking.setCompletedAt(booking.getCompletedAt() == null ? now : booking.getCompletedAt());
            booking.setFinalizedAt(now);
            booking.setCompletionOutcome(BookingCompletionOutcome.COMPLETED_CONFIRMED);
        } else {
            booking.setStatus(BookingStatus.AUTO_CLOSED);
            booking.setAutoClosedAt(now);
            booking.setFinalizedAt(now);
            booking.setCompletionOutcome(BookingCompletionOutcome.COMPLETED_AUTO_CLOSED);
        }

        Booking savedBooking = bookingRepository.save(booking);
        settlementService.releaseForBooking(savedBooking);
        return toBookingResponse(savedBooking);
    }

    @Transactional(readOnly = true)
    public BookingResponse getAdminBookingDetail(UUID bookingId) {
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
        return toBookingResponse(booking);
    }

    private MentorService resolveMentorService(UUID serviceId, UUID mentorUserId) {
        if (serviceId == null) {
            return null;
        }

        MentorService mentorService = mentorServiceRepository.findByIdAndMentorProfileUserIdAndIsActiveTrue(serviceId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.BAD_REQUEST, "Gói mentoring đã chọn không tồn tại hoặc không thuộc mentor này"));
        if (mentorService.getDurationMinutes() == null || mentorService.getDurationMinutes() <= 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Gói mentoring đã chọn có thời lượng không hợp lệ");
        }
        return mentorService;
    }

    private Booking getBookingForMentorDecision(UUID mentorUserId, UUID bookingId) {
        if (mentorUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }

        Booking booking = bookingRepository.findByIdForMentorDecision(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
        if (!booking.getMentorProfile().getUserId().equals(mentorUserId)) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền xử lý booking này");
        }
        if (booking.getMentorProfile().getUser() == null
                || booking.getMentorProfile().getUser().getStatus() != UserStatus.ACTIVE) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor hiện không còn hoạt động");
        }
        return booking;
    }

    private MentorAvailabilitySlot requireLockedSlotForDecision(Booking booking) {
        if (booking == null || booking.getSlot() == null || booking.getSlot().getId() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Booking hiện không gắn với khung giờ hợp lệ");
        }
        return mentorAvailabilitySlotRepository.findByIdForUpdate(booking.getSlot().getId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy khung giờ mentoring"));
    }

    private void assertBookingAccess(Booking booking, UUID currentUserId) {
        boolean isMentee = booking.getMentee() != null && currentUserId.equals(booking.getMentee().getId());
        boolean isMentor = booking.getMentorProfile() != null && currentUserId.equals(booking.getMentorProfile().getUserId());
        if (!isMentee && !isMentor) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền xem booking này");
        }
    }

    private BookingResponse toBookingResponse(Booking booking) {
        return toBookingResponse(booking, null);
    }

    private BookingResponse toBookingResponse(Booking booking, java.util.Map<UUID, UUID> bookingToConversationMap) {
        User mentee = booking.getMentee();
        MentorProfile mentorProfile = booking.getMentorProfile();
        User mentorUser = mentorProfile == null ? null : mentorProfile.getUser();
        MentorService mentorService = booking.getService();
        MentorAvailabilitySlot slot = booking.getSlot();

        com.fptu.exe.skillswap.modules.session.domain.Session session = sessionService != null ? sessionService.findByBookingId(booking.getId()) : null;

        MeetingPlatform platform = session != null ? session.getMeetingPlatform() : booking.getMeetingPlatform();
        String link = session != null ? session.getMeetingLink() : booking.getMeetingLink();
        LocalDateTime actualStart = session != null ? session.getActualStartTime() : booking.getActualStartTime();
        LocalDateTime actualEnd = session != null ? session.getActualEndTime() : booking.getActualEndTime();

        UUID conversationId = null;
        if (bookingToConversationMap != null && bookingToConversationMap.containsKey(booking.getId())) {
            conversationId = bookingToConversationMap.get(booking.getId());
        } else if (conversationService != null) {
            com.fptu.exe.skillswap.modules.conversation.domain.Conversation conv = conversationService.findByBookingId(booking.getId());
            if (conv != null) {
                conversationId = conv.getId();
            }
        }

        UUID currentUserId = null;
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal) {
            currentUserId = ((UserPrincipal) auth.getPrincipal()).getPublicId();
        }

        boolean isMenteeUser = currentUserId != null && mentee != null && currentUserId.equals(mentee.getId());
        boolean isMentorUser = currentUserId != null && mentorProfile != null && currentUserId.equals(mentorProfile.getUserId());

        LocalDateTime now = DateTimeUtil.now();
        LocalDateTime startTime = selectedStartTime(booking);
        LocalDateTime endTime = selectedEndTime(booking);

        boolean canCancel = false;
        boolean canComplete = false;
        boolean canReschedule = false;
        boolean canSubmitFeedback = false;

        if (currentUserId != null) {
            if (booking.getStatus() == BookingStatus.PENDING) {
                canCancel = isMenteeUser;
            } else if (booking.getStatus() == BookingStatus.ACCEPTED_AWAITING_PAYMENT || isConfirmedBookingStatus(booking.getStatus())) {
                canCancel = (isMenteeUser || isMentorUser) && startTime != null && now.isBefore(startTime);
            }

            if (isMentorUser) {
                canComplete = (isConfirmedBookingStatus(booking.getStatus()) || booking.getStatus() == BookingStatus.AWAITING_MENTOR_COMPLETION)
                        && endTime != null && now.isAfter(endTime);
            } else if (isMenteeUser) {
                canComplete = booking.getStatus() == BookingStatus.AWAITING_MENTEE_CONFIRMATION
                        && endTime != null && now.isBefore(endTime.plusHours(24));
            }

            canReschedule = (isMenteeUser || isMentorUser)
                    && isConfirmedBookingStatus(booking.getStatus())
                    && (booking.getRescheduleCount() == null ? 0 : booking.getRescheduleCount()) < 1
                    && startTime != null
                    && java.time.Duration.between(now, startTime).toMinutes() >= 6 * 60;

            canSubmitFeedback = isMenteeUser
                    && (booking.getStatus() == BookingStatus.COMPLETED || booking.getStatus() == BookingStatus.AUTO_CLOSED);
        }

        return BookingResponse.builder()
                .bookingId(booking.getId())
                .sessionId(booking.getId())
                .sessionStatus(booking.getStatus())
                .actualSessionId(session == null ? null : session.getId())
                .actualSessionStatus(session == null ? null : session.getStatus())
                .mentorUserId(mentorProfile == null ? null : mentorProfile.getUserId())
                .mentorDisplayName(mentorUser == null ? null : mentorUser.getFullName())
                .mentorAvatarUrl(mentorUser == null ? null : mentorUser.getAvatarUrl())
                .menteeUserId(mentee == null ? null : mentee.getId())
                .menteeDisplayName(mentee == null ? null : mentee.getFullName())
                .menteeAvatarUrl(mentee == null ? null : mentee.getAvatarUrl())
                .availabilitySlotId(slot == null ? null : slot.getId())
                .serviceId(mentorService == null ? null : mentorService.getId())
                .serviceTitle(booking.getServiceTitleSnapshot() != null ? booking.getServiceTitleSnapshot() : (mentorService == null ? null : mentorService.getTitle()))
                .serviceDescriptionSnapshot(booking.getServiceDescriptionSnapshot())
                .serviceExpectedOutcomeSnapshot(booking.getServiceExpectedOutcomeSnapshot())
                .serviceDurationSnapshot(booking.getServiceDurationSnapshot())
                .serviceIsFreeSnapshot(booking.getServiceIsFreeSnapshot())
                .servicePriceScoinSnapshot(booking.getServicePriceScoinSnapshot())
                .status(booking.getStatus())
                .learningGoalTitle(booking.getLearningGoalTitle())
                .learningGoalDescription(booking.getLearningGoalDescription())
                .mentorResponseNote(booking.getMentorResponseNote())
                .rejectReason(booking.getRejectReason())
                .cancelReason(booking.getCancelReason())
                .meetingPlatform(platform)
                .meetingLink(link)
                .location(booking.getLocation())
                .selectedStartTime(startTime)
                .selectedEndTime(endTime)
                .actualStartTime(actualStart)
                .actualEndTime(actualEnd)
                .acceptedAt(booking.getAcceptedAt())
                .rejectedAt(booking.getRejectedAt())
                .cancelledAt(booking.getCancelledAt())
                .completedAt(booking.getCompletedAt())
                .finalizedAt(booking.getFinalizedAt())
                .autoClosedAt(booking.getAutoClosedAt())
                .completionOutcome(booking.getCompletionOutcome())
                .issueSubmittedAt(booking.getIssueSubmittedAt())
                .issueType(booking.getIssueType())
                .issueDescription(booking.getIssueDescription())
                .wantsAdminReview(booking.getWantsAdminReview())
                .issueResolvedAt(booking.getIssueResolvedAt())
                .issueResolvedByUserId(booking.getIssueResolvedByUserId())
                .issueResolutionNote(booking.getIssueResolutionNote())
                .mentorNote(booking.getMentorNote())
                .menteeNote(booking.getMenteeNote())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .conversationId(conversationId)
                .canCancel(canCancel)
                .canComplete(canComplete)
                .canReschedule(canReschedule)
                .canSubmitFeedback(canSubmitFeedback)
                .build();
    }

    private Pageable bookingPageable(BookingListRequest request) {
        int page = Math.max(request.getPage(), 0);
        int size = Math.min(Math.max(request.getSize(), 1), 20);
        Sort.Direction direction = request.resolveDirection();
        String sortBy = bookingSortBy(request.getSortBy());

        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }

    private Pageable bookingPriorityPageable(BookingListRequest request) {
        int page = Math.max(request.getPage(), 0);
        int size = Math.min(Math.max(request.getSize(), 1), 20);
        return PageRequest.of(page, size);
    }

    private Pageable adminBookingPageable(AdminBookingListRequest request) {
        int page = Math.max(request.getPage(), 0);
        int size = Math.min(Math.max(request.getSize(), 1), 100);
        return PageRequest.of(page, size, Sort.by(request.resolveDirection(), bookingSortBy(request.getSortBy())));
    }

    private String bookingSortBy(String sortBy) {
        return switch (sortBy == null ? "" : sortBy.trim()) {
            case "createdAt" -> "createdAt";
            case "updatedAt" -> "updatedAt";
            case "acceptedAt" -> "acceptedAt";
            case "rejectedAt" -> "rejectedAt";
            case "cancelledAt" -> "cancelledAt";
            case "completedAt" -> "completedAt";
            case "status" -> "status";
            default -> "selectedStartTime";
        };
    }

    private Booking getBookingForCancellation(UUID bookingId) {
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }
        return bookingRepository.findByIdForCancellation(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
    }

    private Booking getBookingForSessionAction(UUID currentUserId, UUID bookingId) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }
        Booking booking = bookingRepository.findByIdForSessionUpdate(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
        assertBookingAccess(booking, currentUserId);
        return booking;
    }

    private boolean isMentorOfBooking(Booking booking, UUID userId) {
        return userId != null
                && booking.getMentorProfile() != null
                && userId.equals(booking.getMentorProfile().getUserId());
    }

    private String requiredCancelReason(CancelBookingRequest request) {
        String reason = trimToNull(request == null ? null : request.cancelReason());
        if (reason == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Lý do hủy booking không được để trống");
        }
        return reason;
    }

    private long minutesUntilStart(Booking booking, LocalDateTime now) {
        if (selectedStartTime(booking) == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thời gian bắt đầu booking không hợp lệ");
        }
        return Duration.between(now, selectedStartTime(booking)).toMinutes();
    }

    private void synchronizePostSessionStatusForPhaseOne(Booking booking, LocalDateTime now) {
        if (booking == null || now == null) {
            return;
        }
        if (isConfirmedBookingStatus(booking.getStatus())
                && selectedEndTime(booking) != null
                && !now.isBefore(selectedEndTime(booking))) {
            booking.setStatus(BookingStatus.AWAITING_MENTOR_COMPLETION);
        }
        if (booking.getStatus() == BookingStatus.AWAITING_MENTEE_CONFIRMATION
                && selectedEndTime(booking) != null
                && now.isAfter(selectedEndTime(booking).plusHours(POST_SESSION_REVIEW_WINDOW_HOURS))) {
            booking.setStatus(BookingStatus.AUTO_CLOSED);
            booking.setAutoClosedAt(now);
            booking.setFinalizedAt(now);
            booking.setCompletionOutcome(BookingCompletionOutcome.COMPLETED_AUTO_CLOSED);
            settlementService.releaseForBooking(booking);
        }
    }

    private void ensureWithinPostSessionReviewWindow(Booking booking, LocalDateTime now) {
        LocalDateTime bookingEnd = selectedEndTime(booking);
        if (bookingEnd == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thời gian kết thúc booking không hợp lệ");
        }
        if (now.isAfter(bookingEnd.plusHours(POST_SESSION_REVIEW_WINDOW_HOURS))) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Đã quá thời hạn 24 giờ để phản hồi sau buổi mentoring");
        }
    }

    private LocalDateTime selectedStartTime(Booking booking) {
        return booking.getSelectedStartTime();
    }

    private LocalDateTime selectedEndTime(Booking booking) {
        return booking.getSelectedEndTime();
    }

    private void applyMentorCancellationPenalty(MentorProfile mentorProfile, long minutesUntilStart, LocalDateTime now) {
        if (minutesUntilStart < MENTOR_SUSPENSION_CANCEL_DEADLINE_MINUTES) {
            mentorProfile.setBookingSuspendedUntil(now.plusDays(MENTOR_LATE_CANCEL_SUSPENSION_DAYS));
            return;
        }
        if (minutesUntilStart < MENTOR_SAFE_CANCEL_DEADLINE_MINUTES) {
            BigDecimal currentPenalty = mentorProfile.getLateCancellationPenaltyPoints() == null
                    ? BigDecimal.ZERO
                    : mentorProfile.getLateCancellationPenaltyPoints();
            mentorProfile.setLateCancellationPenaltyPoints(currentPenalty.add(MENTOR_LATE_CANCEL_PENALTY));
        }
    }

    private String cleanMeetingLink(String meetingLink) {
        String link = trimToNull(meetingLink);
        if (link == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "meetingLink không được để trống");
        }
        try {
            URI uri = URI.create(link);
            String scheme = uri.getScheme();
            if (scheme == null
                    || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null
                    || uri.getHost().isBlank()) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "meetingLink phải là URL hợp lệ bắt đầu bằng http:// hoặc https://");
            }
            return link;
        } catch (IllegalArgumentException ex) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "meetingLink phải là URL hợp lệ");
        }
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }

    private boolean isConfirmedBookingStatus(BookingStatus status) {
        return status == BookingStatus.PAID || status == BookingStatus.ACCEPTED;
    }

    private void refreshSlotBookedFlag(MentorAvailabilitySlot slot) {
        if (slot == null || slot.getId() == null) {
            return;
        }
        boolean hasAcceptedBookings = bookingRepository.existsOverlappingBySlotIdAndStatusIn(
                slot.getId(),
                SLOT_LOCKING_STATUSES,
                slot.getStartTime(),
                slot.getEndTime()
        );
        slot.setBooked(hasAcceptedBookings);
    }

    private int defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }

    @Transactional
    public void rejectAllPendingBookingsForMentor(UUID mentorUserId, String reason) {
        if (mentorUserId == null) {
            return;
        }
        List<Booking> pendingBookings = bookingRepository.findByMentorProfileUserIdAndStatus(mentorUserId, BookingStatus.PENDING);
        if (pendingBookings.isEmpty()) {
            return;
        }
        for (Booking booking : pendingBookings) {
            booking.setStatus(BookingStatus.REJECTED);
            booking.setRejectedAt(DateTimeUtil.now());
            booking.setRejectReason(reason);
            if (booking.getSlot() != null) {
                booking.getSlot().setBooked(false);
            }
        }
        bookingRepository.saveAll(pendingBookings);
        for (Booking booking : pendingBookings) {
            eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.notification.event.NotificationEvent(
                    booking.getMentee().getId(),
                    com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_AUTO_REJECTED,
                    "Yêu cầu đặt lịch không còn hiệu lực",
                    buildAutoRejectedMessage(reason),
                    "BOOKING",
                    booking.getId()
            ));
            eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent(
                    booking.getId(),
                    booking.getMentee().getId(),
                    booking.getMentorProfile().getUserId(),
                    booking.getStatus(),
                    "Yêu cầu đặt lịch không còn hiệu lực.",
                    booking.getUpdatedAt() != null ? booking.getUpdatedAt() : DateTimeUtil.now()
            ));
        }
    }

    @Transactional
    public BookingResponse cancelBookingByMentor(UUID mentorUserId, UUID bookingId, CancelBookingRequest request) {
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }
        UUID slotId = bookingRepository.findSlotIdByBookingId(bookingId).orElse(null);
        if (slotId != null) {
            mentorAvailabilitySlotRepository.findByIdForUpdate(slotId);
        }

        Booking booking = getBookingForCancellation(bookingId);
        if (!isMentorOfBooking(booking, mentorUserId)) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền hủy booking này");
        }
        if (booking.getStatus() != BookingStatus.ACCEPTED_AWAITING_PAYMENT && !isConfirmedBookingStatus(booking.getStatus())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor chỉ có thể hủy booking đã được chấp nhận");
        }

        LocalDateTime now = DateTimeUtil.now();
        long minutesUntilStart = minutesUntilStart(booking, now);
        if (minutesUntilStart <= 0) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking đã bắt đầu, không thể hủy bằng luồng này");
        }

        booking.setStatus(BookingStatus.CANCELLED_BY_MENTOR);
        booking.setCancelledAt(now);
        booking.setCancelReason(requiredCancelReason(request));

        MentorAvailabilitySlot slot = booking.getSlot();
        refreshSlotBookedFlag(slot);

        MentorProfile mentorProfile = booking.getMentorProfile();
        if (mentorProfile != null) {
            MentorProfile lockedProfile = mentorProfileRepository.findByIdForUpdate(mentorProfile.getUserId())
                    .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ mentor"));
            entityManager.refresh(lockedProfile);
            applyMentorCancellationPenalty(lockedProfile, minutesUntilStart, now);
            mentorProfileRepository.save(lockedProfile);
        }

        Booking savedBooking = bookingRepository.save(booking);

        sessionService.cancelForBooking(bookingId);
        paymentOrderService.handleMentorCancellation(savedBooking);

        eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.notification.event.NotificationEvent(
                savedBooking.getMentee().getId(),
                com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_CANCELLED_BY_MENTOR,
                "Mentor đã hủy lịch",
                savedBooking.getMentorProfile().getUser().getFullName() + " đã hủy lịch mentoring.",
                "BOOKING",
                savedBooking.getId()
        ));

        eventPublisher.publishEvent(com.fptu.exe.skillswap.modules.booking.event.BookingEmailNotificationEvent.builder()
                .bookingId(savedBooking.getId())
                .eventType(com.fptu.exe.skillswap.modules.booking.event.BookingEmailNotificationEvent.EventType.BOOKING_CANCELLED_BY_MENTOR_EMAIL)
                .recipientEmail(savedBooking.getMentee().getEmail())
                .recipientName(savedBooking.getMentee().getFullName())
                .actorName(savedBooking.getMentorProfile().getUser().getFullName())
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
                .reason(savedBooking.getCancelReason())
                .createdAt(DateTimeUtil.now())
                .build());

        eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent(
                savedBooking.getId(),
                savedBooking.getMentee().getId(),
                savedBooking.getMentorProfile().getUserId(),
                savedBooking.getStatus(),
                "Mentor đã hủy lịch học.",
                savedBooking.getUpdatedAt() != null ? savedBooking.getUpdatedAt() : DateTimeUtil.now()
        ));
        return toBookingResponse(savedBooking);
    }

    @Transactional
    public BookingResponse cancelBookingByMentee(UUID menteeId, UUID bookingId, CancelBookingRequest request) {
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }
        UUID slotId = bookingRepository.findSlotIdByBookingId(bookingId).orElse(null);
        if (slotId != null) {
            mentorAvailabilitySlotRepository.findByIdForUpdate(slotId);
        }

        Booking booking = getBookingForCancellation(bookingId);
        if (menteeId == null || booking.getMentee() == null || !menteeId.equals(booking.getMentee().getId())) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền hủy booking này");
        }
        if (booking.getStatus() != BookingStatus.PENDING
                && booking.getStatus() != BookingStatus.ACCEPTED_AWAITING_PAYMENT
                && !isConfirmedBookingStatus(booking.getStatus())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể hủy booking đang chờ phản hồi hoặc đã được chấp nhận");
        }

        LocalDateTime now = DateTimeUtil.now();
        BookingStatus currentStatus = booking.getStatus();
        long minutesUntilStart = isConfirmedBookingStatus(currentStatus)
                ? minutesUntilStart(booking, now)
                : Long.MAX_VALUE;
        boolean lateCancellation = isConfirmedBookingStatus(currentStatus)
                && minutesUntilStart < MENTEE_FREE_CANCEL_DEADLINE_MINUTES;

        booking.setStatus(BookingStatus.CANCELLED_BY_MENTEE);
        booking.setCancelledAt(now);
        booking.setCancelReason(requiredCancelReason(request));

        MentorAvailabilitySlot slot = booking.getSlot();
        if (slot != null && currentStatus != BookingStatus.PENDING) {
            refreshSlotBookedFlag(slot);
        }

        Booking savedBooking = bookingRepository.save(booking);

        sessionService.cancelForBooking(bookingId);
        if (currentStatus == BookingStatus.ACCEPTED_AWAITING_PAYMENT || isConfirmedBookingStatus(currentStatus)) {
            paymentOrderService.handleMenteeCancellation(savedBooking, lateCancellation);
        }

        eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.notification.event.NotificationEvent(
                savedBooking.getMentorProfile().getUserId(),
                com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_CANCELLED_BY_MENTEE,
                "Mentee đã hủy lịch",
                savedBooking.getMentee().getFullName() + " đã hủy lịch mentoring.",
                "BOOKING",
                savedBooking.getId()
        ));

        eventPublisher.publishEvent(com.fptu.exe.skillswap.modules.booking.event.BookingEmailNotificationEvent.builder()
                .bookingId(savedBooking.getId())
                .eventType(com.fptu.exe.skillswap.modules.booking.event.BookingEmailNotificationEvent.EventType.BOOKING_CANCELLED_BY_MENTEE_EMAIL)
                .recipientEmail(savedBooking.getMentorProfile().getUser().getEmail())
                .recipientName(savedBooking.getMentorProfile().getUser().getFullName())
                .actorName(savedBooking.getMentee().getFullName())
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
                .reason(savedBooking.getCancelReason())
                .createdAt(DateTimeUtil.now())
                .build());

        eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent(
                savedBooking.getId(),
                savedBooking.getMentee().getId(),
                savedBooking.getMentorProfile().getUserId(),
                savedBooking.getStatus(),
                "Mentee đã hủy lịch học.",
                savedBooking.getUpdatedAt() != null ? savedBooking.getUpdatedAt() : DateTimeUtil.now()
        ));
        return toBookingResponse(savedBooking);
    }

    @Transactional
    public int expireStalePendingBookings() {
        LocalDateTime now = DateTimeUtil.now();
        String reason = "Yêu cầu đặt lịch đã tự động hết hạn do vượt quá thời gian bắt đầu.";
        List<Booking> staleBookings = bookingRepository.findByStatusAndSelectedStartTimeBeforeOrderBySelectedStartTimeAsc(
                BookingStatus.PENDING,
                now
        );
        if (staleBookings.isEmpty()) {
            return 0;
        }
        for (Booking booking : staleBookings) {
            booking.setStatus(BookingStatus.REJECTED);
            booking.setRejectedAt(now);
            booking.setRejectReason(reason);
            if (booking.getSlot() != null) {
                booking.getSlot().setBooked(false);
            }
        }
        bookingRepository.saveAll(staleBookings);
        for (Booking booking : staleBookings) {
            eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.notification.event.NotificationEvent(
                    booking.getMentee().getId(),
                    com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_REQUEST_EXPIRED,
                    "Yêu cầu đặt lịch đã hết hạn",
                    "Yêu cầu đặt lịch của bạn đã tự động hết hạn vì mentor chưa phản hồi trước giờ bắt đầu. Bạn có thể chọn khung giờ khác để đặt lịch lại.",
                    "BOOKING",
                    booking.getId()
            ));
            eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent(
                    booking.getId(),
                    booking.getMentee().getId(),
                    booking.getMentorProfile().getUserId(),
                    booking.getStatus(),
                    "Yêu cầu đặt lịch đã hết hạn.",
                    booking.getUpdatedAt() != null ? booking.getUpdatedAt() : DateTimeUtil.now()
            ));
        }
        return staleBookings.size();
    }

    @Transactional
    public int expireAwaitingPaymentBookings() {
        LocalDateTime now = DateTimeUtil.now();
        List<Booking> staleBookings = bookingRepository.findByStatusAndAcceptedAtBeforeOrderByAcceptedAtAsc(
                BookingStatus.ACCEPTED_AWAITING_PAYMENT,
                now.minusMinutes(PAYMENT_WINDOW_MINUTES)
        );
        if (staleBookings.isEmpty()) {
            return 0;
        }
        for (Booking booking : staleBookings) {
            booking.setStatus(BookingStatus.EXPIRED);
            booking.setRejectedAt(now);
            booking.setRejectReason("Yêu cầu đặt lịch đã hết hạn do mentee chưa hoàn tất thanh toán trong vòng 2 giờ.");
            if (booking.getSlot() != null
                    && booking.getSlot().getStartTime() != null
                    && now.isBefore(booking.getSlot().getStartTime())) {
                refreshSlotBookedFlag(booking.getSlot());
            }
            paymentOrderService.expireAwaitingPayment(booking);
        }
        bookingRepository.saveAll(staleBookings);
        for (Booking booking : staleBookings) {
            eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.notification.event.NotificationEvent(
                    booking.getMentee().getId(),
                    com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_PAYMENT_EXPIRED,
                    "Yêu cầu đặt lịch đã hết hạn thanh toán",
                    "Yêu cầu đặt lịch của bạn đã tự động hết hạn vì chưa hoàn tất thanh toán trong vòng 2 giờ.",
                    "BOOKING",
                    booking.getId()
            ));
            eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent(
                    booking.getId(),
                    booking.getMentee().getId(),
                    booking.getMentorProfile().getUserId(),
                    booking.getStatus(),
                    "Yêu cầu đặt lịch đã hết hạn thanh toán.",
                    booking.getUpdatedAt() != null ? booking.getUpdatedAt() : DateTimeUtil.now()
            ));
        }
        return staleBookings.size();
    }

    private String buildAutoRejectedMessage(String reason) {
        String normalizedReason = trimToNull(reason);
        if (normalizedReason == null) {
            return "Yêu cầu đặt lịch của bạn đã bị từ chối do thay đổi về trạng thái nhận lịch của mentor.";
        }
        return "Yêu cầu đặt lịch của bạn đã bị từ chối: " + normalizedReason;
    }
}




