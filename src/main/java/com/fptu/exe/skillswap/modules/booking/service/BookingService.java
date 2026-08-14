package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.booking.constant.BookingQueueConstants;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingCompletionOutcome;
import com.fptu.exe.skillswap.modules.booking.domain.BookingLifecycleStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingPaymentStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStateMapper;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingDisplayState;
import com.fptu.exe.skillswap.modules.booking.domain.BookingNextAction;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MeetingPlatform;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingIssueResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminBookingListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminResolveBookingIssueRequest;
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
import com.fptu.exe.skillswap.modules.booking.dto.request.RespondBookingIssueRequest;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentTargetType;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.service.MentorBookingPolicyService;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.modules.system.service.InternalTelemetryService;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.modules.booking.service.SessionService;
import com.fptu.exe.skillswap.modules.chat.service.ConversationService;
import com.fptu.exe.skillswap.modules.payment.service.SettlementService;
import com.fptu.exe.skillswap.modules.payment.service.PaymentOrderService;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class BookingService {

    private static final long MENTEE_FREE_CANCEL_DEADLINE_MINUTES = 4 * 60;
    private static final long MENTOR_SAFE_CANCEL_DEADLINE_MINUTES = 6 * 60;
    private static final long MENTOR_SUSPENSION_CANCEL_DEADLINE_MINUTES = 3 * 60;
    private static final BigDecimal MENTOR_LATE_CANCEL_PENALTY = BigDecimal.valueOf(0.5);
    private static final int MENTOR_LATE_CANCEL_SUSPENSION_DAYS = 3;
    private static final long POST_SESSION_REVIEW_WINDOW_HOURS = 6;
    private static final long PAYMENT_WINDOW_MINUTES = BookingDeadlinePolicy.PAYMENT_WINDOW_MINUTES;
    private static final String PAYMENT_DEADLINE_TEXT = "6 giờ hoặc ít nhất 1 giờ trước giờ bắt đầu, tùy thời điểm nào đến trước";
    private static final int MIN_SERVICE_PRICE_SCOIN_PER_MINUTE = 1_200;
    private static final int MAX_SERVICE_PRICE_SCOIN_PER_MINUTE = 500_000;
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
    private final PaymentOrderRepository paymentOrderRepository;
    private final BookingEventService bookingEventService;
    private final EntityManager entityManager;
    private final com.fptu.exe.skillswap.modules.booking.service.SessionService sessionService;
    private final com.fptu.exe.skillswap.modules.chat.service.ConversationService conversationService;
    private final com.fptu.exe.skillswap.modules.payment.service.SettlementService settlementService;
    private final com.fptu.exe.skillswap.modules.payment.service.PaymentOrderService paymentOrderService;
    private final BookingSlotValidator bookingSlotValidator;
    private final BookingEligibilityPolicy bookingEligibilityPolicy;
    private final MentorBookingPolicyService mentorBookingPolicyService;
    private final PaymentProperties paymentProperties;
    private final InternalTelemetryService internalTelemetryService;
    private final BookingLifecycleMaintenanceService bookingLifecycleMaintenanceService;
    private AvailabilityTemplateService availabilityTemplateService;

    @Autowired(required = false)
    void setAvailabilityTemplateService(AvailabilityTemplateService availabilityTemplateService) {
        this.availabilityTemplateService = availabilityTemplateService;
    }

    public BookingService(
            BookingRepository bookingRepository,
            MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository,
            MentorServiceRepository mentorServiceRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            ApplicationEventPublisher eventPublisher,
            MentorProfileRepository mentorProfileRepository,
            EntityManager entityManager,
            SessionService sessionService,
            ConversationService conversationService,
            SettlementService settlementService,
            PaymentOrderService paymentOrderService,
            BookingSlotValidator bookingSlotValidator,
            BookingEligibilityPolicy bookingEligibilityPolicy,
            PaymentProperties paymentProperties,
            InternalTelemetryService internalTelemetryService
    ) {
        this(bookingRepository,
                mentorAvailabilitySlotRepository,
                mentorServiceRepository,
                userRepository,
                notificationService,
                eventPublisher,
                mentorProfileRepository,
                null,
                null,
                entityManager,
                sessionService,
                conversationService,
                settlementService,
                paymentOrderService,
                bookingSlotValidator,
                bookingEligibilityPolicy,
                 null,
                 paymentProperties,
                internalTelemetryService,
                new BookingLifecycleMaintenanceService(
                        bookingRepository,
                        mentorProfileRepository,
                        paymentOrderService,
                        settlementService,
                        eventPublisher,
                        null,
                        userRepository));
    }

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
        if (requestedStartAt == null || (request.legacySelectedEndTime() == null
                && (requestedStartAt.getEpochSecond() % 60 != 0 || requestedStartAt.getNano() != 0))) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "SCHEDULING_TIME_PRECISION_INVALID");
        }
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

        return toBookingResponse(savedBooking);
    }

    @Transactional
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

        recoverAwaitingPaymentBookings(page.getContent());

        java.util.List<UUID> bookingIds = page.getContent().stream().map(Booking::getId).toList();
        java.util.Map<UUID, UUID> bookingToConvMap = conversationService != null
                ? conversationService.findConversationIdsForBookings(page.getContent())
                : java.util.Collections.emptyMap();
        java.util.Map<UUID, com.fptu.exe.skillswap.modules.booking.domain.Session> sessionsByBookingId = sessionService != null
                ? sessionService.findByBookingIds(bookingIds)
                : java.util.Collections.emptyMap();
        java.util.Map<UUID, com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder> paymentOrdersByBookingId =
                bookingIds.isEmpty() || paymentOrderRepository == null
                        ? java.util.Collections.emptyMap()
                        : paymentOrderRepository.findByTargetTypeAndTargetIdIn(PaymentTargetType.BOOKING, bookingIds).stream()
                        .collect(Collectors.toMap(
                                com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder::getTargetId,
                                Function.identity(),
                                (left, right) -> left,
                                java.util.LinkedHashMap::new
                        ));

        return PageResponse.<BookingResponse>builder()
                .content(page.getContent().stream()
                        .map(b -> toBookingResponse(b, bookingToConvMap, sessionsByBookingId, paymentOrdersByBookingId))
                        .toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Transactional
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
        recoverAwaitingPaymentBooking(booking);
        ensureSessionExistsForConfirmedBooking(booking);

        com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder paymentOrder = paymentOrderRepository == null
                ? null
                : paymentOrderRepository.findByTargetTypeAndTargetId(PaymentTargetType.BOOKING, bookingId).orElse(null);
        return toBookingResponse(booking, null, null,
                paymentOrder == null ? java.util.Collections.emptyMap()
                        : java.util.Map.of(bookingId, paymentOrder));
    }

    @Transactional
    public BookingResponse acceptBooking(UUID mentorUserId, UUID bookingId, AcceptBookingRequest request) {
        if (mentorUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }

        // 1. Load the slot ID and mentee ID without loading the whole booking entity to prevent session caching issues
        UUID slotId = bookingRepository.findSlotIdByBookingId(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking hoặc booking không gắn với khung giờ mentoring"));
        UUID menteeId = bookingRepository.findMenteeIdByBookingId(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking hoặc booking không gắn với mentee"));

        // Lock both participants in UUID order before any mutable booking resource.
        // This serializes same-mentor and same-mentee accepts without creating a
        // cycle between a user lock held by one transaction and a slot lock held by another.
        java.util.List<UUID> participantIds = java.util.stream.Stream.of(mentorUserId, menteeId)
                .distinct()
                .sorted()
                .toList();
        if (userRepository.findAllByIdInOrderByIdForUpdate(participantIds).size() != participantIds.size()) {
            throw new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng tham gia booking");
        }
        MentorProfile lockedMentorProfile = mentorProfileRepository.findByIdForUpdate(mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ mentor"));

        // Lock the slot after participant resources and before the booking row.
        MentorAvailabilitySlot slot = mentorAvailabilitySlotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy khung giờ mentoring"));

        // Lock the target booking after users, profile and slot.
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
        if (booking.getPendingExpireAt() != null && !booking.getPendingExpireAt().isAfter(now)) {
            // The scheduler owns terminal cleanup, but an interactive accept must never bypass the SLA.
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT,
                    "Yêu cầu đặt lịch đã quá hạn phản hồi và không thể được chấp nhận.");
        }
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

        // 4.5. Lock Mentee overlapping bookings to prevent double booking race condition
        List<Booking> menteeOverlappingBookings = bookingRepository.findMenteeOverlappingBookingsForUpdate(
                booking.getMentee().getId(),
                SLOT_LOCKING_STATUSES,
                selectedStartTime,
                selectedEndTime
        );
        if (!menteeOverlappingBookings.isEmpty()) {
            booking.setStatus(BookingStatus.REJECTED);
            booking.setRejectedAt(now);
            booking.setRejectReason("Mentee đã có lịch học khác trùng thời gian này");
            bookingRepository.save(booking);
            return toBookingResponse(booking);
        }

        // 5. Lock sibling pending bookings overlap the same selected segment in deterministic order
        List<Booking> pendingBookings = bookingRepository.findOverlappingBySlotIdAndStatusForUpdate(
                slot.getId(),
                BookingStatus.PENDING,
                selectedStartTime,
                selectedEndTime
        );
        List<Booking> menteePendingBookings = bookingRepository.findMenteeOverlappingBookingsForUpdate(
                booking.getMentee().getId(),
                List.of(BookingStatus.PENDING),
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

        entityManager.refresh(lockedMentorProfile);
        lockedMentorProfile.setTotalAcceptedBookings(defaultInteger(lockedMentorProfile.getTotalAcceptedBookings()) + 1);
        touchMentorActivity(lockedMentorProfile, now);
        mentorProfileRepository.save(lockedMentorProfile);

        for (Booking pendingBooking : pendingBookings) {
            if (pendingBooking.getId().equals(booking.getId())) {
                continue;
            }
            pendingBooking.setStatus(BookingStatus.REJECTED);
            pendingBooking.setRejectedAt(now);
            pendingBooking.setRejectReason(BookingQueueConstants.AUTO_REJECT_SLOT_ACCEPTED_REASON);
        }

        // A newly accepted booking reserves both participants. Pending requests for this
        // mentee at other mentors are no longer actionable and must not remain queued.
        for (Booking pendingBooking : menteePendingBookings) {
            if (pendingBooking.getId().equals(booking.getId())) {
                continue;
            }
            pendingBooking.setStatus(BookingStatus.REJECTED);
            pendingBooking.setRejectedAt(now);
            pendingBooking.setRejectReason("MENTEE_TIME_LOCKED_BY_OTHER_BOOKING");
        }

        bookingRepository.saveAll(new java.util.ArrayList<>(java.util.stream.Stream.concat(pendingBookings.stream(), menteePendingBookings.stream())
                .collect(java.util.stream.Collectors.toMap(Booking::getId, value -> value, (left, right) -> left))
                .values()));
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
                    savedBooking.getMentorProfile().getUser().getFullName()
                            + " đã chấp nhận lịch mentoring của bạn. Vui lòng hoàn tất thanh toán trong vòng "
                            + PAYMENT_DEADLINE_TEXT + ".",
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
                    .paymentDeadline(resolvePaymentDeadline(savedBooking))
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
            touchMentorActivity(lockedProfile, DateTimeUtil.now());
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

        com.fptu.exe.skillswap.modules.booking.domain.Session session = sessionService.findByBookingId(bookingId);
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
        requireDirectBooking(booking);
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
        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(BookingStatus.AWAITING_MENTEE_CONFIRMATION);
        booking.setCompletedAt(now);

        if (booking.getActualStartTime() == null) {
            booking.setActualStartTime(selectedStartTime(booking));
        }
        if (booking.getActualEndTime() == null) {
            booking.setActualEndTime(selectedEndTime(booking));
        }

        com.fptu.exe.skillswap.modules.booking.domain.Session session = sessionService.findByBookingId(bookingId);
        if (session != null) {
            session.setStatus(com.fptu.exe.skillswap.modules.booking.domain.SessionStatus.COMPLETED);
            if (session.getActualStartTime() == null) {
                session.setActualStartTime(selectedStartTime(booking));
            }
            if (session.getActualEndTime() == null) {
                session.setActualEndTime(selectedEndTime(booking));
            }
        }

        MentorProfile mentorProfile = booking.getMentorProfile();
        if (mentorProfile != null) {
            MentorProfile lockedProfile = mentorProfileRepository.findByIdForUpdate(mentorProfile.getUserId())
                    .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ mentor"));
            entityManager.refresh(lockedProfile);
            touchMentorActivity(lockedProfile, now);
            mentorProfileRepository.save(lockedProfile);
        }

        Booking savedBooking = bookingRepository.save(booking);
        recordBookingEvent(savedBooking, com.fptu.exe.skillswap.modules.booking.domain.BookingEventType.MENTOR_COMPLETED,
                oldStatus, com.fptu.exe.skillswap.modules.booking.domain.BookingEventActorType.USER, mentorUserId, null);
        eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.notification.event.NotificationEvent(
                savedBooking.getMentee().getId(),
                com.fptu.exe.skillswap.modules.notification.domain.NotificationType.SESSION_COMPLETED,
                "Mentor đã xác nhận hoàn tất buổi mentoring",
                "Buổi mentoring đã chờ bạn xác nhận hoặc báo vấn đề trong " + POST_SESSION_REVIEW_WINDOW_HOURS + " giờ.",
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

        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setFinalizedAt(now);
        booking.setCompletionOutcome(BookingCompletionOutcome.USER_CONFIRMED);
        booking.setMenteeNote(trimToNull(request == null ? null : request.confirmationNote()));

        MentorProfile mentorProfile = booking.getMentorProfile();
        if (mentorProfile != null) {
            MentorProfile lockedProfile = mentorProfileRepository.findByIdForUpdate(mentorProfile.getUserId())
                    .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ mentor"));
            entityManager.refresh(lockedProfile);
            lockedProfile.setTotalCompletedSessions(defaultInteger(lockedProfile.getTotalCompletedSessions()) + 1);
            lockedProfile.setTotalSessions(defaultInteger(lockedProfile.getTotalSessions()) + 1);
            touchMentorActivity(lockedProfile, now);
            mentorProfileRepository.save(lockedProfile);
        }

        Booking savedBooking = bookingRepository.save(booking);
        settlementService.releaseForBooking(savedBooking);
        recordBookingEvent(savedBooking, com.fptu.exe.skillswap.modules.booking.domain.BookingEventType.MENTEE_CONFIRMED,
                oldStatus, com.fptu.exe.skillswap.modules.booking.domain.BookingEventActorType.USER, currentUserId, null);
        internalTelemetryService.record(
                "BOOKING_COMPLETED",
                currentUserId,
                "BOOKING",
                savedBooking.getId(),
                Map.of(
                        "mentorUserId", String.valueOf(savedBooking.getMentorProfile() == null ? null : savedBooking.getMentorProfile().getUserId()),
                        "completionOutcome", String.valueOf(savedBooking.getCompletionOutcome())
                )
        );
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
        ensureWithinIssueWindow(booking, now);
        validateIssueReporter(booking, currentUserId, request.issueType());

        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(BookingStatus.UNDER_REVIEW);
        booking.setIssueSubmittedAt(now);
        booking.setIssueSubmittedByUserId(currentUserId);
        booking.setIssueType(request.issueType());
        booking.setIssueDescription(trim(request.description()));
        booking.setCompletionOutcome(BookingCompletionOutcome.UNDER_REVIEW);

        Booking savedBooking = bookingRepository.save(booking);
        recordBookingEvent(savedBooking, com.fptu.exe.skillswap.modules.booking.domain.BookingEventType.ISSUE_CREATED,
                oldStatus, com.fptu.exe.skillswap.modules.booking.domain.BookingEventActorType.USER, currentUserId, null);
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
                .issueType(savedBooking.getIssueType())
                .issueRespondedAt(savedBooking.getIssueRespondedAt())
                .build();
    }

    @Transactional
    public BookingIssueResponse respondToBookingIssue(UUID currentUserId, UUID bookingId, RespondBookingIssueRequest request) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (request == null || trimToNull(request.responseNote()) == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu nội dung phản hồi issue");
        }
        Booking booking = getBookingForSessionAction(currentUserId, bookingId);
        assertBookingAccess(booking, currentUserId);
        if (booking.getStatus() != BookingStatus.UNDER_REVIEW || booking.getIssueSubmittedAt() == null) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking hiện không có issue đang mở");
        }
        if (booking.getIssueRespondedAt() != null) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Issue này đã có phản hồi từ counterparty");
        }
        if (currentUserId.equals(booking.getIssueSubmittedByUserId())) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Chỉ counterparty của người báo issue mới được phản hồi");
        }
        LocalDateTime now = DateTimeUtil.now();
        if (now.isAfter(booking.getIssueSubmittedAt().plusHours(24))) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Đã quá thời hạn phản hồi issue");
        }
        booking.setIssueRespondedAt(now);
        booking.setIssueRespondedByUserId(currentUserId);
        booking.setIssueResponseNote(trimToNull(request.responseNote()));
        Booking saved = bookingRepository.save(booking);
        recordBookingEvent(saved, com.fptu.exe.skillswap.modules.booking.domain.BookingEventType.ISSUE_RESPONDED,
                BookingStatus.UNDER_REVIEW, com.fptu.exe.skillswap.modules.booking.domain.BookingEventActorType.USER, currentUserId, null);
        return BookingIssueResponse.builder().bookingId(saved.getId()).status(saved.getStatus())
                .issueSubmittedAt(saved.getIssueSubmittedAt()).issueType(saved.getIssueType())
                .issueRespondedAt(saved.getIssueRespondedAt()).build();
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
                ? conversationService.findConversationIdsForBookings(page.getContent())
                : java.util.Collections.emptyMap();
        java.util.Map<UUID, com.fptu.exe.skillswap.modules.booking.domain.Session> sessionsByBookingId = sessionService != null
                ? sessionService.findByBookingIds(bookingIds)
                : java.util.Collections.emptyMap();

        return PageResponse.<BookingResponse>builder()
                .content(page.getContent().stream().map(b -> toBookingResponse(b, bookingToConvMap, sessionsByBookingId)).toList())
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

        BookingStatus oldStatus = booking.getStatus();
        if (request.action() == com.fptu.exe.skillswap.modules.booking.domain.AdminBookingIssueResolutionAction.CONFIRM_SESSION) {
            booking.setStatus(BookingStatus.COMPLETED);
            booking.setCompletedAt(booking.getCompletedAt() == null ? now : booking.getCompletedAt());
            booking.setFinalizedAt(now);
            booking.setCompletionOutcome(BookingCompletionOutcome.USER_CONFIRMED);
            settlementService.releaseForBooking(booking);
        } else if (request.action() == com.fptu.exe.skillswap.modules.booking.domain.AdminBookingIssueResolutionAction.CONFIRM_MENTOR_NO_SHOW_REFUND) {
            booking.setStatus(BookingStatus.COMPLETED);
            booking.setFinalizedAt(now);
            booking.setCompletionOutcome(BookingCompletionOutcome.NO_SHOW_MENTOR);
            settlementService.refundForMentorNoShow(booking);
            incrementMentorNoShow(booking);
        } else {
            booking.setStatus(BookingStatus.COMPLETED);
            booking.setFinalizedAt(now);
            booking.setCompletionOutcome(BookingCompletionOutcome.NO_SHOW_MENTEE);
            settlementService.releaseForBooking(booking);
        }

        Booking savedBooking = bookingRepository.save(booking);

        recordBookingEvent(savedBooking, com.fptu.exe.skillswap.modules.booking.domain.BookingEventType.ISSUE_RESOLVED,
                oldStatus, com.fptu.exe.skillswap.modules.booking.domain.BookingEventActorType.ADMIN, adminUserId, null);
        return toBookingResponse(savedBooking);
    }

    @Transactional(readOnly = true)
    public BookingResponse getAdminBookingDetail(UUID bookingId) {
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
        ensureSessionExistsForConfirmedBooking(booking);
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
        validateServicePricing(mentorService.isFree(), mentorService.getPriceScoin(), mentorService.getDurationMinutes());
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
        requireDirectBooking(booking);
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
        return toBookingResponse(booking, null, null, null);
    }

    private BookingResponse toBookingResponse(Booking booking, java.util.Map<UUID, UUID> bookingToConversationMap) {
        return toBookingResponse(booking, bookingToConversationMap, null, null);
    }

    private BookingResponse toBookingResponse(Booking booking,
                                              java.util.Map<UUID, UUID> bookingToConversationMap,
                                              java.util.Map<UUID, com.fptu.exe.skillswap.modules.booking.domain.Session> sessionsByBookingId) {
        return toBookingResponse(booking, bookingToConversationMap, sessionsByBookingId, null);
    }

    private BookingResponse toBookingResponse(Booking booking,
                                              java.util.Map<UUID, UUID> bookingToConversationMap,
                                              java.util.Map<UUID, com.fptu.exe.skillswap.modules.booking.domain.Session> sessionsByBookingId,
                                              java.util.Map<UUID, com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder> paymentOrdersByBookingId) {
        User mentee = booking.getMentee();
        MentorProfile mentorProfile = booking.getMentorProfile();
        User mentorUser = mentorProfile == null ? null : mentorProfile.getUser();
        MentorService mentorService = booking.getService();
        MentorAvailabilitySlot slot = booking.getSlot();
        com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder paymentOrder = resolvePaymentOrder(
                booking,
                paymentOrdersByBookingId
        );

        com.fptu.exe.skillswap.modules.booking.domain.Session session = null;
        if (sessionsByBookingId != null) {
            session = sessionsByBookingId.get(booking.getId());
        }
        if (session == null && sessionService != null) {
            session = sessionService.findByBookingId(booking.getId());
        }

        MeetingPlatform platform = session != null ? session.getMeetingPlatform() : booking.getMeetingPlatform();
        String link = session != null ? session.getMeetingLink() : booking.getMeetingLink();

        LocalDateTime actualStart = session != null ? session.getActualStartTime() : booking.getActualStartTime();
        LocalDateTime actualEnd = session != null ? session.getActualEndTime() : booking.getActualEndTime();

        UUID conversationId = null;
        if (bookingToConversationMap != null && bookingToConversationMap.containsKey(booking.getId())) {
            conversationId = bookingToConversationMap.get(booking.getId());

        } else if (conversationService != null) {
            com.fptu.exe.skillswap.modules.chat.domain.Conversation conv = conversationService.findByBookingId(booking.getId());
            if (conv == null && mentee != null && mentee.getId() != null && mentorUser != null && mentorUser.getId() != null) {
                conv = conversationService.findDirectByParticipants(mentorUser.getId(), mentee.getId());
            }
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
                canCancel = (isMenteeUser || isMentorUser)
                        && startTime != null && now.isBefore(startTime);
            }

            if (isMentorUser) {
                canComplete = (isConfirmedBookingStatus(booking.getStatus()) || booking.getStatus() == BookingStatus.AWAITING_MENTOR_COMPLETION)
                        && endTime != null && now.isAfter(endTime);
            } else if (isMenteeUser) {
                canComplete = booking.getStatus() == BookingStatus.AWAITING_MENTEE_CONFIRMATION
                        && booking.getCompletedAt() != null
                        && now.isBefore(booking.getCompletedAt().plusHours(POST_SESSION_REVIEW_WINDOW_HOURS));
            }

            canReschedule = (isMenteeUser || isMentorUser)
                    && isConfirmedBookingStatus(booking.getStatus())
                    && (booking.getRescheduleCount() == null ? 0 : booking.getRescheduleCount()) < 1
                    && startTime != null
                    && java.time.Duration.between(now, startTime).toMinutes() >= 6 * 60;

            canSubmitFeedback = isMenteeUser
                    && booking.getStatus() == BookingStatus.COMPLETED
                    && BookingStateMapper.toCanonicalCompletionOutcome(booking) == BookingCompletionOutcome.USER_CONFIRMED;
        }

        BookingLifecycleStatus bookingLifecycleStatus = BookingStateMapper.toLifecycleStatus(booking);
        BookingPaymentStatus bookingPaymentStatus = BookingStateMapper.toPaymentStatus(booking, paymentOrder);
        BookingCompletionOutcome completionOutcome = booking.getCompletionOutcome();
        if (completionOutcome == null) {
            completionOutcome = BookingStateMapper.toCanonicalCompletionOutcome(booking);
        }
        BookingDisplayGuidance displayGuidance = deriveDisplayGuidance(
                booking, isMenteeUser, isMentorUser, now, startTime, endTime, completionOutcome, canSubmitFeedback
        );

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
                .maintainPostSessionChatSnapshot(booking.isMaintainPostSessionChatSnapshot())
                .servicePriceWithSurchargeScoin(calculateMenteeVisiblePrice(booking.getServiceIsFreeSnapshot(), booking.getServicePriceScoinSnapshot()))
                .status(booking.getStatus())
                .bookingStatus(bookingLifecycleStatus)
                .paymentStatus(bookingPaymentStatus)
                .settlementStatus(paymentOrder == null ? null : paymentOrder.getSettlementStatus())
                .releasedAt(paymentOrder == null ? null : paymentOrder.getReleasedAt())
                .refundedAt(paymentOrder == null ? null : paymentOrder.getRefundedAt())
                .refundedScoin(paymentOrder == null ? null : paymentOrder.getRefundedScoin())
                .refundReason(paymentOrder == null ? null : paymentOrder.getRefundReason())
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
                .pendingExpireAt(booking.getPendingExpireAt())
                .rejectedAt(booking.getRejectedAt())
                .cancelledAt(booking.getCancelledAt())
                .completedAt(booking.getCompletedAt())
                .finalizedAt(booking.getFinalizedAt())
                .autoClosedAt(booking.getAutoClosedAt())
                .completionOutcome(completionOutcome)
                .issueSubmittedAt(booking.getIssueSubmittedAt())
                .issueType(booking.getIssueType())
                .issueDescription(booking.getIssueDescription())
                .issueRespondedAt(booking.getIssueRespondedAt())
                .issueRespondedByUserId(booking.getIssueRespondedByUserId())
                .issueResponseNote(booking.getIssueResponseNote())
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
                .cancellationRefundPolicy(BookingCancellationRefundPolicy.current())
                .displayState(displayGuidance.state())
                .nextAction(displayGuidance.action())
                .actionDeadlineAt(displayGuidance.deadlineAt())

                .build();
    }

    private BookingDisplayGuidance deriveDisplayGuidance(
            Booking booking,
            boolean isMentee,
            boolean isMentor,
            LocalDateTime now,
            LocalDateTime startTime,
            LocalDateTime endTime,
            BookingCompletionOutcome outcome,
            boolean canSubmitFeedback
    ) {
        if (booking.getStatus() == BookingStatus.UNDER_REVIEW || outcome == BookingCompletionOutcome.UNDER_REVIEW) {
            return new BookingDisplayGuidance(BookingDisplayState.UNDER_REVIEW, BookingNextAction.VIEW_ISSUE, null);
        }
        if (booking.getStatus() == BookingStatus.CANCELLED_BY_MENTEE || booking.getStatus() == BookingStatus.CANCELLED_BY_MENTOR
                || booking.getStatus() == BookingStatus.REJECTED || booking.getStatus() == BookingStatus.EXPIRED || booking.getStatus() == BookingStatus.NO_SHOW) {
            return new BookingDisplayGuidance(BookingDisplayState.CANCELED_OR_EXPIRED, BookingNextAction.NONE, null);
        }
        if (booking.getStatus() == BookingStatus.PENDING) {
            return isMentor
                    ? new BookingDisplayGuidance(BookingDisplayState.MENTOR_ACTION_REQUIRED, BookingNextAction.ACCEPT_OR_REJECT, booking.getPendingExpireAt())
                    : new BookingDisplayGuidance(BookingDisplayState.PENDING_MENTOR_RESPONSE, BookingNextAction.NONE, booking.getPendingExpireAt());
        }
        if (booking.getStatus() == BookingStatus.ACCEPTED_AWAITING_PAYMENT) {
            return isMentee
                    ? new BookingDisplayGuidance(BookingDisplayState.PAYMENT_REQUIRED, BookingNextAction.PAY_NOW, resolvePaymentDeadline(booking))
                    : new BookingDisplayGuidance(BookingDisplayState.PAYMENT_REQUIRED, BookingNextAction.NONE, resolvePaymentDeadline(booking));
        }
        if (booking.getStatus() == BookingStatus.AWAITING_MENTEE_CONFIRMATION) {
            return new BookingDisplayGuidance(BookingDisplayState.WAITING_CONFIRMATION,
                    isMentee ? BookingNextAction.CONFIRM_SESSION : BookingNextAction.NONE,
                    booking.getCompletedAt() == null ? null : booking.getCompletedAt().plusHours(POST_SESSION_REVIEW_WINDOW_HOURS));
        }
        if (booking.getStatus() == BookingStatus.AWAITING_MENTOR_COMPLETION) {
            return new BookingDisplayGuidance(BookingDisplayState.MENTOR_ACTION_REQUIRED,
                    isMentor ? BookingNextAction.COMPLETE_SESSION : BookingNextAction.NONE, null);
        }
        if (booking.getStatus() == BookingStatus.COMPLETED || booking.getStatus() == BookingStatus.AUTO_CLOSED) {
            return canSubmitFeedback
                    ? new BookingDisplayGuidance(BookingDisplayState.FEEDBACK_REQUIRED, BookingNextAction.LEAVE_FEEDBACK, null)
                    : new BookingDisplayGuidance(BookingDisplayState.COMPLETED, BookingNextAction.NONE, null);
        }
        if (endTime != null && !now.isBefore(endTime)) {
            return new BookingDisplayGuidance(BookingDisplayState.MENTOR_ACTION_REQUIRED,
                    isMentor ? BookingNextAction.COMPLETE_SESSION : BookingNextAction.NONE, null);
        }
        if (startTime != null && !now.isBefore(startTime) && (endTime == null || now.isBefore(endTime))) {
            return new BookingDisplayGuidance(BookingDisplayState.IN_SESSION, BookingNextAction.NONE, null);
        }
        return new BookingDisplayGuidance(BookingDisplayState.UPCOMING, BookingNextAction.NONE, null);
    }

    private record BookingDisplayGuidance(BookingDisplayState state, BookingNextAction action, LocalDateTime deadlineAt) {}

    private com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder resolvePaymentOrder(
            Booking booking,
            java.util.Map<UUID, com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder> paymentOrdersByBookingId
    ) {
        if (booking == null || booking.getId() == null) {
            return null;
        }
        if (paymentOrdersByBookingId != null && paymentOrdersByBookingId.containsKey(booking.getId())) {
            return paymentOrdersByBookingId.get(booking.getId());
        }
        if (paymentOrderRepository == null) {
            return null;
        }
        return paymentOrderRepository.findByTargetTypeAndTargetId(PaymentTargetType.BOOKING, booking.getId()).orElse(null);
    }

    private int calculateMenteeVisiblePrice(Boolean isFree, Integer basePriceScoin) {
        int price = basePriceScoin == null ? 0 : Math.max(0, basePriceScoin);
        if (Boolean.TRUE.equals(isFree) || price == 0) {
            return 0;
        }
        int surchargeBps = paymentProperties == null ? 1000 : paymentProperties.getMenteeSurchargeBps();
        long total = (long) price + ((long) price * surchargeBps) / 10_000L;
        if (total > Integer.MAX_VALUE) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Giá hiển thị cho mentee vượt giới hạn hệ thống");
        }
        return (int) total;
    }

    private int normalizedServicePrice(MentorService mentorService) {
        if (mentorService == null) {
            return 0;
        }
        validateServicePricing(mentorService.isFree(), mentorService.getPriceScoin(), mentorService.getDurationMinutes());
        return Boolean.TRUE.equals(mentorService.isFree()) ? 0 : defaultInteger(mentorService.getPriceScoin());
    }

    private void validateServicePricing(Boolean isFree, Integer priceScoin, Integer durationMinutes) {
        if (Boolean.TRUE.equals(isFree)) {
            if (priceScoin != null && priceScoin > 0) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Gói mentoring miễn phí phải có giá bằng 0");
            }
            return;
        }
        if (durationMinutes == null || durationMinutes <= 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Gói mentoring đã chọn có thời lượng không hợp lệ");
        }
        int normalizedPrice = defaultInteger(priceScoin);
        if (normalizedPrice <= 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Gói mentoring có phí phải có giá lớn hơn 0");
        }
        int minimumPrice = durationMinutes * MIN_SERVICE_PRICE_SCOIN_PER_MINUTE;
        if (normalizedPrice < minimumPrice) {
            throw new BaseException(
                    ErrorCode.BAD_REQUEST,
                    "Gói mentoring có phí phải có giá tối thiểu " + minimumPrice + " SCoin cho " + durationMinutes + " phút"
            );
        }
        int maximumPrice = durationMinutes * MAX_SERVICE_PRICE_SCOIN_PER_MINUTE;
        if (normalizedPrice > maximumPrice) {
            throw new BaseException(
                    ErrorCode.BAD_REQUEST,
                    "Gói mentoring có phí chỉ được đặt tối đa " + maximumPrice + " SCoin cho " + durationMinutes + " phút"
            );
        }
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

    /** Group attendance, meeting and completion semantics are introduced separately in Phase 3. */
    private void requireDirectBooking(Booking booking) {
        // No-op after GroupSession removal
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
                && booking.getStatus() != BookingStatus.AWAITING_MENTEE_CONFIRMATION
                && selectedEndTime(booking) != null
                && !now.isBefore(selectedEndTime(booking))) {
            booking.setStatus(BookingStatus.AWAITING_MENTOR_COMPLETION);
        }
    }

    private void ensureWithinPostSessionReviewWindow(Booking booking, LocalDateTime now) {
        LocalDateTime reviewAnchor = postSessionReviewAnchor(booking);
        if (reviewAnchor == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thời gian kết thúc booking không hợp lệ");
        }
        long windowHours = POST_SESSION_REVIEW_WINDOW_HOURS;
        if (now.isAfter(reviewAnchor.plusHours(windowHours))) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Đã quá thời hạn " + windowHours + " giờ để phản hồi sau buổi mentoring");
        }
    }

    private void ensureWithinIssueWindow(Booking booking, LocalDateTime now) {
        LocalDateTime anchor = booking.getCompletedAt() == null ? selectedEndTime(booking) : booking.getCompletedAt();
        if (anchor == null || now.isAfter(anchor.plusHours(booking.getCompletedAt() == null ? 24 : POST_SESSION_REVIEW_WINDOW_HOURS))) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Đã quá thời hạn để báo vấn đề sau buổi mentoring");
        }
    }

    private void validateIssueReporter(Booking booking, UUID currentUserId, BookingIssueType issueType) {
        if (issueType == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Loại issue không hợp lệ");
        }
        boolean mentor = isMentorOfBooking(booking, currentUserId);
        if ((issueType == BookingIssueType.MENTOR_NO_SHOW && mentor)
                || (issueType == BookingIssueType.MENTEE_NO_SHOW && !mentor)) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền báo loại issue này");
        }
    }

    private void incrementMentorNoShow(Booking booking) {
        MentorProfile profile = booking.getMentorProfile();
        if (profile == null) {
            return;
        }
        MentorProfile locked = mentorProfileRepository.findByIdForUpdate(profile.getUserId()).orElse(null);
        if (locked != null) {
            locked.setMentorNoShowCount(defaultInteger(locked.getMentorNoShowCount()) + 1);
        }
    }

    private void incrementMentorCompletionOverdue(Booking booking) {
        MentorProfile profile = booking.getMentorProfile();
        if (profile == null) {
            return;
        }
        MentorProfile locked = mentorProfileRepository.findByIdForUpdate(profile.getUserId()).orElse(null);
        if (locked != null) {
            locked.setMentorCompletionOverdueCount(defaultInteger(locked.getMentorCompletionOverdueCount()) + 1);
        }
    }

    private void recordBookingEvent(Booking booking, com.fptu.exe.skillswap.modules.booking.domain.BookingEventType eventType,
                                    BookingStatus oldStatus, com.fptu.exe.skillswap.modules.booking.domain.BookingEventActorType actorType,
                                    UUID actorUserId, String metadataJson) {
        if (bookingEventService != null) {
            bookingEventService.record(booking, eventType, oldStatus, actorType, actorUserId, metadataJson);
        }
    }

    private LocalDateTime postSessionReviewAnchor(Booking booking) {
        if (booking == null) {
            return null;
        }
        if (booking.getCompletedAt() != null) {
            return booking.getCompletedAt();
        }
        return selectedEndTime(booking);
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
        return BookingStateMapper.isLegacyConfirmedForScheduling(status);
    }

    private void recoverAwaitingPaymentBookings(List<Booking> bookings) {
        if (bookings == null || bookings.isEmpty()) {
            return;
        }
        for (Booking booking : bookings) {
            recoverAwaitingPaymentBooking(booking);
        }
    }

    private void recoverAwaitingPaymentBooking(Booking booking) {
        if (booking == null || booking.getId() == null || booking.getStatus() != BookingStatus.ACCEPTED_AWAITING_PAYMENT) {
            return;
        }
        paymentOrderService.synchronizeProviderStatusForBooking(booking.getId());
    }

    private void ensureSessionExistsForConfirmedBooking(Booking booking) {

        if (booking == null || booking.getId() == null || sessionService == null) {
            return;
        }
        if (booking.getStatus() != BookingStatus.PAID && booking.getStatus() != BookingStatus.ACCEPTED) {
            return;
        }
        if (sessionService.findByBookingId(booking.getId()) == null) {
            sessionService.createForAcceptedBooking(booking);
        }
    }

    private LocalDateTime resolvePaymentDeadline(Booking booking) {

        if (booking == null) {
            return null;
        }
        LocalDateTime startDeadline = booking.getSelectedStartTime();
        if (startDeadline == null && booking.getSlot() != null) {
            startDeadline = booking.getSlot().getStartTime();
        }
        return BookingDeadlinePolicy.resolvePaymentDeadline(booking.getAcceptedAt(), startDeadline);
    }

    private boolean isPaymentDeadlineReached(Booking booking, LocalDateTime now) {
        LocalDateTime deadline = resolvePaymentDeadline(booking);
        return deadline != null && !deadline.isAfter(now);
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
        if (availabilityTemplateService != null) {
            availabilityTemplateService.markSlotDue(slot.getId());
        }
    }

    private void touchMentorActivity(MentorProfile mentorProfile, LocalDateTime activityTime) {
        if (mentorProfile == null || activityTime == null) {
            return;
        }
        if (mentorProfile.getLastActiveAt() == null || mentorProfile.getLastActiveAt().isBefore(activityTime)) {
            mentorProfile.setLastActiveAt(activityTime);
        }
    }

    private int defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }

    @Transactional
    public void rejectAllPendingBookingsForMentor(UUID mentorUserId, String reason) {
        bookingLifecycleMaintenanceService.rejectAllPendingBookingsForMentor(mentorUserId, reason);
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
            lockedProfile.setTotalMentorCancelledBookings(defaultInteger(lockedProfile.getTotalMentorCancelledBookings()) + 1);
            touchMentorActivity(lockedProfile, now);
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
        String reason = "Yêu cầu đặt lịch đã tự động hết hạn vì mentor chưa phản hồi đúng hạn.";
        List<Booking> staleBookings = bookingRepository.findByStatusAndPendingExpireAtLessThanEqualOrderByPendingExpireAtAsc(
                BookingStatus.PENDING,
                now
        );
        if (staleBookings.isEmpty()) {
            return 0;
        }
        for (Booking booking : staleBookings) {
            booking.setStatus(BookingStatus.EXPIRED);
            booking.setRejectedAt(now);
            booking.setRejectReason(reason);
            if (booking.getSlot() != null) {
                refreshSlotBookedFlag(booking.getSlot());
            }
        }
        bookingRepository.saveAll(staleBookings);
        for (Booking booking : staleBookings) {
            eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.notification.event.NotificationEvent(
                    booking.getMentee().getId(),
                    com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_REQUEST_EXPIRED,
                    "Yêu cầu đặt lịch đã hết hạn",
                    "Yêu cầu đặt lịch của bạn đã tự động hết hạn vì mentor chưa phản hồi đúng hạn. Bạn có thể chọn khung giờ khác để đặt lịch lại.",
                    "BOOKING",
                    booking.getId()
            ));
            eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.notification.event.NotificationEvent(
                    booking.getMentorProfile().getUserId(),
                    com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_REQUEST_EXPIRED,
                    "Yêu cầu đặt lịch đã được giải phóng",
                    "Một yêu cầu booking chưa được phản hồi đã hết hạn. Khung giờ hiện có thể nhận yêu cầu khác.",
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
        LocalDateTime acceptedAtCutoff = now.minusMinutes(PAYMENT_WINDOW_MINUTES);
        List<UUID> staleBookingIds = bookingRepository.findAwaitingPaymentExpiryCandidates(
                BookingStatus.ACCEPTED_AWAITING_PAYMENT,
                acceptedAtCutoff,
                now.plusMinutes(BookingDeadlinePolicy.PAYMENT_PREPARATION_MINUTES)
        ).stream().map(Booking::getId).toList();
        if (staleBookingIds.isEmpty()) {
            return 0;
        }
        List<Booking> staleBookings = new ArrayList<>();
        for (UUID bookingId : staleBookingIds) {
            Booking booking = bookingRepository.findByIdForSessionUpdate(bookingId).orElse(null);
            if (booking == null
                    || booking.getStatus() != BookingStatus.ACCEPTED_AWAITING_PAYMENT
                    || !isPaymentDeadlineReached(booking, now)) {
                continue;
            }
            booking.setStatus(BookingStatus.EXPIRED);
            booking.setRejectedAt(now);
            booking.setRejectReason("Yêu cầu đặt lịch đã hết hạn do mentee chưa hoàn tất thanh toán trong vòng "
                    + PAYMENT_DEADLINE_TEXT + ".");
            if (booking.getSlot() != null
                    && booking.getSlot().getStartTime() != null
                    && now.isBefore(booking.getSlot().getStartTime())) {
                refreshSlotBookedFlag(booking.getSlot());
            }
            paymentOrderService.expireAwaitingPayment(booking);
            staleBookings.add(booking);
        }
        if (staleBookings.isEmpty()) {
            return 0;
        }
        bookingRepository.saveAll(staleBookings);
        for (Booking booking : staleBookings) {
            eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.notification.event.NotificationEvent(
                    booking.getMentee().getId(),
                    com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_PAYMENT_EXPIRED,
                    "Yêu cầu đặt lịch đã hết hạn thanh toán",
                    "Yêu cầu đặt lịch của bạn đã tự động hết hạn vì chưa hoàn tất thanh toán trong vòng "
                            + PAYMENT_DEADLINE_TEXT + ".",
                    "BOOKING",
                    booking.getId()
            ));
            eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.notification.event.NotificationEvent(
                    booking.getMentorProfile().getUserId(),
                    com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_PAYMENT_EXPIRED,
                    "Khung giờ đã được giải phóng",
                    "Mentee chưa hoàn tất thanh toán đúng hạn. Khung giờ mentoring hiện có thể nhận yêu cầu khác.",
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

    /**
     * Single-node post-session worker. Each candidate is reloaded with a row lock before a
     * transition, so retrying this method is safe and never relies on a stale read.
     */
    @Transactional
    public int processPostSessionLifecycle() {
        LocalDateTime now = DateTimeUtil.now();
        int changed = 0;
        List<Booking> confirmed = new ArrayList<>();
        confirmed.addAll(bookingRepository.findTop100ByStatusAndSelectedEndTimeBeforeOrderBySelectedEndTimeAsc(BookingStatus.PAID, now));
        confirmed.addAll(bookingRepository.findTop100ByStatusAndSelectedEndTimeBeforeOrderBySelectedEndTimeAsc(BookingStatus.ACCEPTED, now));
        for (Booking candidate : confirmed) {
            changed += processPostSessionCandidate(candidate.getId(), now) ? 1 : 0;
        }
        for (Booking candidate : bookingRepository.findTop100ByStatusAndSelectedEndTimeBeforeOrderBySelectedEndTimeAsc(
                BookingStatus.AWAITING_MENTOR_COMPLETION, now)) {
            changed += processPostSessionCandidate(candidate.getId(), now) ? 1 : 0;
        }
        for (Booking candidate : bookingRepository.findTop100ByStatusAndCompletedAtBeforeOrderByCompletedAtAsc(
                BookingStatus.AWAITING_MENTEE_CONFIRMATION, now.minusHours(5))) {
            changed += processPostSessionCandidate(candidate.getId(), now) ? 1 : 0;
        }
        for (Booking candidate : bookingRepository.findTop100ByStatusAndIssueSubmittedAtBeforeOrderByIssueSubmittedAtAsc(
                BookingStatus.UNDER_REVIEW, now.minusHours(12))) {
            changed += processIssueDeadline(candidate.getId(), now) ? 1 : 0;
        }
        for (Booking candidate : bookingRepository.findTop100ByStatusAndIssueSubmittedAtBeforeAndAdminSlaWarningSentAtIsNullAndIssueResolvedAtIsNullOrderByIssueSubmittedAtAsc(
                BookingStatus.UNDER_REVIEW, now.minusHours(48))) {
            changed += processAdminDisputeSlaWarning(candidate.getId(), now) ? 1 : 0;
        }
        return changed;
    }

    private boolean processPostSessionCandidate(UUID bookingId, LocalDateTime now) {
        Booking booking = bookingRepository.findByIdForSessionUpdate(bookingId).orElse(null);
        if (booking == null) {
            return false;
        }
        BookingStatus oldStatus = booking.getStatus();
        if ((booking.getStatus() == BookingStatus.PAID || booking.getStatus() == BookingStatus.ACCEPTED)
                && selectedEndTime(booking) != null && !now.isBefore(selectedEndTime(booking))) {
            booking.setStatus(BookingStatus.AWAITING_MENTOR_COMPLETION);
            booking.setPostSessionPromptedAt(now);
            recordBookingEvent(booking, com.fptu.exe.skillswap.modules.booking.domain.BookingEventType.POST_SESSION_STARTED,
                    oldStatus, com.fptu.exe.skillswap.modules.booking.domain.BookingEventActorType.SYSTEM, null, null);
            notifyPostSessionPrompt(booking);
            return true;
        }
        if (booking.getStatus() == BookingStatus.AWAITING_MENTOR_COMPLETION) {
            LocalDateTime end = selectedEndTime(booking);
            if (end == null) {
                return false;
            }
            if (booking.getMentorCompletionReminder30mAt() == null && !now.isBefore(end.plusMinutes(30))) {
                booking.setMentorCompletionReminder30mAt(now);
                notifyMentor(booking, "Nhắc xác nhận hoàn tất", "Buổi mentoring đã kết thúc. Vui lòng xác nhận hoàn tất trong thời hạn cho phép.");
                return true;
            }
            if (booking.getMentorCompletionReminder1hAt() == null && !now.isBefore(end.plusHours(3))) {
                booking.setMentorCompletionReminder1hAt(now);
                notifyMentor(booking, "Nhắc xác nhận hoàn tất", "Bạn vẫn chưa xác nhận hoàn tất buổi mentoring.");
                return true;
            }
            if (booking.getMenteeCompletionPromptedAt() == null && !now.isBefore(end.plusHours(2))) {
                booking.setMenteeCompletionPromptedAt(now);
                notifyMentee(booking, "Chưa nhận được xác nhận từ mentor", "Bạn có thể tiếp tục chờ hoặc báo vấn đề nếu buổi mentoring không diễn ra.");
                return true;
            }
            if (booking.getMentorCompletionOverdueAt() == null && !now.isBefore(end.plusHours(24))) {
                booking.setMentorCompletionOverdueAt(now);
                incrementMentorCompletionOverdue(booking);
                recordBookingEvent(booking, com.fptu.exe.skillswap.modules.booking.domain.BookingEventType.MENTOR_COMPLETION_OVERDUE,
                        BookingStatus.AWAITING_MENTOR_COMPLETION, com.fptu.exe.skillswap.modules.booking.domain.BookingEventActorType.SYSTEM, null, null);
                notifyMentor(booking, "Booking quá hạn xác nhận", "Booking đã được đưa vào hàng chờ vận hành vì bạn chưa xác nhận hoàn tất.");
                return true;
            }
            return false;
        }
        if (booking.getStatus() == BookingStatus.AWAITING_MENTEE_CONFIRMATION && booking.getCompletedAt() != null) {
            if (booking.getAutoCloseWarningSentAt() == null && !now.isBefore(booking.getCompletedAt().plusHours(5))) {
                booking.setAutoCloseWarningSentAt(now);
                notifyMentee(booking, "Buổi mentoring sắp tự đóng", "Bạn còn một giờ để xác nhận hoặc báo vấn đề.");
                notifyMentor(booking, "Buổi mentoring sắp tự đóng", "Nếu không có issue, settlement sẽ được release khi booking tự đóng.");
                return true;
            }
            if (!now.isBefore(booking.getCompletedAt().plusHours(6))) {
                booking.setStatus(BookingStatus.COMPLETED);
                booking.setAutoClosedAt(now);
                booking.setFinalizedAt(now);
                booking.setCompletionOutcome(BookingCompletionOutcome.AUTO_CLOSED);
                settlementService.releaseForBooking(booking);
                recordBookingEvent(booking, com.fptu.exe.skillswap.modules.booking.domain.BookingEventType.AUTO_CLOSED,
                        BookingStatus.AWAITING_MENTEE_CONFIRMATION, com.fptu.exe.skillswap.modules.booking.domain.BookingEventActorType.SYSTEM, null, null);
                return true;
            }
        }
        return false;
    }

    private boolean processIssueDeadline(UUID bookingId, LocalDateTime now) {
        Booking booking = bookingRepository.findByIdForSessionUpdate(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.UNDER_REVIEW || booking.getIssueSubmittedAt() == null) {
            return false;
        }
        if ((booking.getIssueType() != BookingIssueType.MENTOR_NO_SHOW && booking.getIssueType() != BookingIssueType.MENTEE_NO_SHOW)
                || booking.getIssueRespondedAt() != null) {
            return false;
        }
        if (booking.getIssueEscalationSentAt() == null && !now.isBefore(booking.getIssueSubmittedAt().plusHours(12))) {
            booking.setIssueEscalationSentAt(now);
            if (booking.getIssueType() == BookingIssueType.MENTOR_NO_SHOW) {
                notifyMentor(booking, "Cần phản hồi issue booking", "Bạn có 12 giờ còn lại để phản hồi báo cáo no-show.");
            } else {
                notifyMentee(booking, "Cần phản hồi issue booking", "Bạn có 12 giờ còn lại để phản hồi báo cáo no-show.");
            }
            return true;
        }
        if (!now.isBefore(booking.getIssueSubmittedAt().plusHours(24))) {
            BookingStatus old = booking.getStatus();
            booking.setStatus(BookingStatus.COMPLETED);
            booking.setFinalizedAt(now);
            if (booking.getIssueType() == BookingIssueType.MENTOR_NO_SHOW) {
                booking.setCompletionOutcome(BookingCompletionOutcome.NO_SHOW_MENTOR);
                settlementService.refundForMentorNoShow(booking);
                incrementMentorNoShow(booking);
            } else {
                booking.setCompletionOutcome(BookingCompletionOutcome.NO_SHOW_MENTEE);
                settlementService.releaseForBooking(booking);
            }
            booking.setIssueResolvedAt(now);
            booking.setIssueResolutionNote("SYSTEM_AUTO_RESOLUTION_NO_COUNTERPARTY_RESPONSE");
            recordBookingEvent(booking, com.fptu.exe.skillswap.modules.booking.domain.BookingEventType.ISSUE_RESOLVED,
                    old, com.fptu.exe.skillswap.modules.booking.domain.BookingEventActorType.SYSTEM, null, null);
            return true;
        }
        return false;
    }

    private boolean processAdminDisputeSlaWarning(UUID bookingId, LocalDateTime now) {
        Booking booking = bookingRepository.findByIdForSessionUpdate(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.UNDER_REVIEW
                || booking.getIssueSubmittedAt() == null || booking.getAdminSlaWarningSentAt() != null
                || booking.getIssueResolvedAt() != null
                || now.isBefore(booking.getIssueSubmittedAt().plusHours(48))) {
            return false;
        }
        booking.setAdminSlaWarningSentAt(now);
        bookingRepository.save(booking);
        recordBookingEvent(booking, com.fptu.exe.skillswap.modules.booking.domain.BookingEventType.ADMIN_SLA_WARNING_SENT,
                booking.getStatus(), com.fptu.exe.skillswap.modules.booking.domain.BookingEventActorType.SYSTEM, null, null);
        notifyAdminsDisputeSlaBreach(booking);
        return true;
    }

    private void notifyAdminsDisputeSlaBreach(Booking booking) {
        if (userRepository == null) {
            return;
        }
        List<User> admins = new ArrayList<>();
        admins.addAll(userRepository.findUsersByRole(RoleCode.ADMIN, Pageable.unpaged()).getContent());
        admins.addAll(userRepository.findUsersByRole(RoleCode.SYSTEM_ADMIN, Pageable.unpaged()).getContent());
        java.util.Map<UUID, User> activeAdmins = admins.stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        for (User admin : activeAdmins.values()) {
            eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.notification.event.NotificationEvent(
                    admin.getId(),
                    com.fptu.exe.skillswap.modules.notification.domain.NotificationType.ADMIN_DISPUTE_SLA_BREACH,
                    "Cảnh báo SLA Khiếu nại Booking",
                    "Booking #" + booking.getId() + " có khiếu nại chưa được Admin xử lý sau 48 giờ.",
                    "BOOKING",
                    booking.getId()
            ));
        }
    }

    private void notifyPostSessionPrompt(Booking booking) {
        notifyMentor(booking, "Buổi mentoring đã kết thúc", "Vui lòng xác nhận hoàn tất trong 24 giờ.");
        notifyMentee(booking, "Buổi mentoring đã kết thúc", "Bạn có thể báo vấn đề nếu buổi mentoring không diễn ra như mong đợi.");
    }

    private void notifyMentor(Booking booking, String title, String message) {
        if (booking.getMentorProfile() != null) {
            eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.notification.event.NotificationEvent(
                    booking.getMentorProfile().getUserId(), com.fptu.exe.skillswap.modules.notification.domain.NotificationType.SESSION_COMPLETED,
                    title, message, "BOOKING", booking.getId()));
        }
    }

    private void notifyMentee(Booking booking, String title, String message) {
        if (booking.getMentee() != null) {
            eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.notification.event.NotificationEvent(
                    booking.getMentee().getId(), com.fptu.exe.skillswap.modules.notification.domain.NotificationType.SESSION_COMPLETED,
                    title, message, "BOOKING", booking.getId()));
        }
    }

    private String buildAutoRejectedMessage(String reason) {
        String normalizedReason = trimToNull(reason);
        if (normalizedReason == null) {
            return "Yêu cầu đặt lịch của bạn đã bị từ chối do thay đổi về trạng thái nhận lịch của mentor.";
        }
        return "Yêu cầu đặt lịch của bạn đã bị từ chối: " + normalizedReason;
    }
}




