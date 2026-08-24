package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminBookingListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminResolveBookingIssueRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.AcceptBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.BookingListRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.CancelBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.CompleteBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.ConfirmBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.RejectBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.RespondBookingIssueRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.SaveMeetingLinkRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.SubmitBookingIssueRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingIssueResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.booking.repository.SessionRepository;
import com.fptu.exe.skillswap.modules.booking.service.meeting.MeetingProviderFactory;
import com.fptu.exe.skillswap.modules.chat.service.ConversationService;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.service.UserQueryPortImpl;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.mentor.service.MentorBookingPolicyService;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.modules.payment.service.PaymentOrderService;
import com.fptu.exe.skillswap.modules.payment.service.SettlementService;
import com.fptu.exe.skillswap.modules.system.service.InternalTelemetryService;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.UUID;

/**
 * Facade điều phối toàn bộ các nghiệp vụ liên quan đến Booking.
 * Tuân thủ Single Responsibility Principle (SRP) bằng cách ủy quyền cho các Sub-Services chuyên biệt.
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class BookingService {

    private final BookingCreationService bookingCreationService;
    private final BookingDecisionService bookingDecisionService;
    private final BookingCancellationService bookingCancellationService;
    private final BookingCompletionService bookingCompletionService;
    private final BookingMeetingService bookingMeetingService;
    private final BookingQueryService bookingQueryService;
    private final BookingLifecycleMaintenanceService bookingLifecycleMaintenanceService;
    private final BookingResponseMapper bookingResponseMapper;

    /**
     * Constructor tương thích ngược hỗ trợ Unit Tests và Spring DI truyền thống.
     */
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
            InternalTelemetryService internalTelemetryService,
            SessionRepository sessionRepository
    ) {
        UserQueryPortImpl userPort = new UserQueryPortImpl(userRepository, entityManager);
        com.fptu.exe.skillswap.modules.mentor.port.MentorQueryPort mentorPort =
                new com.fptu.exe.skillswap.modules.mentor.service.MentorQueryPortImpl(mentorProfileRepository, mentorServiceRepository);
        PaymentOrderRepository paymentOrderRepo = null;
        BookingResponseMapper mapper = new BookingResponseMapper(sessionService, conversationService, paymentOrderRepo, paymentProperties);
        BookingEventService bookingEventService = null;
        MeetingProviderFactory meetingFactory = new MeetingProviderFactory(Collections.emptyList());

        this.bookingResponseMapper = mapper;
        this.bookingCreationService = new BookingCreationService(
                bookingRepository,
                mentorAvailabilitySlotRepository,
                userPort,
                userPort,
                mentorPort,
                bookingSlotValidator,
                bookingEligibilityPolicy,
                eventPublisher,
                internalTelemetryService,
                mapper,
                null
        );
        this.bookingDecisionService = new BookingDecisionService(
                bookingRepository,
                mentorAvailabilitySlotRepository,
                userPort,
                mentorProfileRepository,
                entityManager,
                sessionService,
                conversationService,
                eventPublisher,
                mapper
        );
        this.bookingCancellationService = new BookingCancellationService(
                bookingRepository,
                mentorAvailabilitySlotRepository,
                mentorProfileRepository,
                entityManager,
                sessionService,
                paymentOrderService,
                eventPublisher,
                mapper
        );
        SessionFinalizationService sessionFinalizationService = new SessionFinalizationService(
                sessionRepository,
                sessionService,
                mentorProfileRepository
        );
        this.bookingCompletionService = new BookingCompletionService(
                bookingRepository,
                sessionFinalizationService,
                settlementService,
                bookingEventService,
                eventPublisher,
                internalTelemetryService,
                mapper
        );
        this.bookingMeetingService = new BookingMeetingService(
                bookingRepository,
                sessionService,
                eventPublisher,
                meetingFactory,
                mapper
        );
        this.bookingQueryService = new BookingQueryService(
                bookingRepository,
                sessionService,
                conversationService,
                paymentOrderRepo,
                mapper
        );
        this.bookingLifecycleMaintenanceService = new BookingLifecycleMaintenanceService(
                bookingRepository,
                paymentOrderService,
                settlementService,
                eventPublisher,
                bookingEventService
        );
        this.bookingLifecycleMaintenanceService.setSessionFinalizationService(sessionFinalizationService);
    }

    @Transactional
    public BookingResponse createBooking(UUID menteeUserId, CreateBookingRequest request) {
        return bookingCreationService.createBooking(menteeUserId, request);
    }

    @Transactional
    public BookingResponse acceptBooking(UUID mentorUserId, UUID bookingId, AcceptBookingRequest request) {
        return bookingDecisionService.acceptBooking(mentorUserId, bookingId, request);
    }

    @Transactional
    public BookingResponse rejectBooking(UUID mentorUserId, UUID bookingId, RejectBookingRequest request) {
        return bookingDecisionService.rejectBooking(mentorUserId, bookingId, request);
    }

    @Transactional
    public BookingResponse cancelBookingByMentor(UUID mentorUserId, UUID bookingId, CancelBookingRequest request) {
        return bookingCancellationService.cancelBookingByMentor(mentorUserId, bookingId, request);
    }

    @Transactional
    public BookingResponse cancelBookingByMentee(UUID menteeId, UUID bookingId, CancelBookingRequest request) {
        return bookingCancellationService.cancelBookingByMentee(menteeId, bookingId, request);
    }

    @Transactional
    public BookingResponse completeBooking(UUID currentUserId, UUID bookingId, CompleteBookingRequest request) {
        return bookingCompletionService.completeBooking(currentUserId, bookingId, request);
    }

    @Transactional
    public BookingResponse completeBookingByMentor(UUID mentorUserId, UUID bookingId, CompleteBookingRequest request) {
        return bookingCompletionService.completeBookingByMentor(mentorUserId, bookingId, request);
    }

    @Transactional
    public BookingResponse confirmBookingByParticipant(UUID currentUserId, UUID bookingId, ConfirmBookingRequest request) {
        return bookingCompletionService.confirmBookingByParticipant(currentUserId, bookingId, request);
    }

    @Transactional
    public BookingIssueResponse submitBookingIssue(UUID currentUserId, UUID bookingId, SubmitBookingIssueRequest request) {
        return bookingCompletionService.submitBookingIssue(currentUserId, bookingId, request);
    }

    @Transactional
    public BookingIssueResponse respondToBookingIssue(UUID currentUserId, UUID bookingId, RespondBookingIssueRequest request) {
        return bookingCompletionService.respondToBookingIssue(currentUserId, bookingId, request);
    }

    @Transactional
    public BookingResponse resolveBookingIssue(UUID adminUserId, UUID bookingId, AdminResolveBookingIssueRequest request) {
        return bookingCompletionService.resolveBookingIssue(adminUserId, bookingId, request);
    }

    @Transactional
    public BookingResponse saveMeetingLink(UUID mentorUserId, UUID bookingId, SaveMeetingLinkRequest request) {
        return bookingMeetingService.saveMeetingLink(mentorUserId, bookingId, request);
    }

    @Transactional
    public PageResponse<BookingResponse> getMyBookings(UUID currentUserId, BookingListRequest request) {
        return bookingQueryService.getMyBookings(currentUserId, request);
    }

    @Transactional
    public BookingResponse getBookingDetail(UUID currentUserId, UUID bookingId) {
        return bookingQueryService.getBookingDetail(currentUserId, bookingId);
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> getAdminBookings(AdminBookingListRequest request) {
        return bookingQueryService.getAdminBookings(request);
    }

    @Transactional(readOnly = true)
    public BookingResponse getAdminBookingDetail(UUID bookingId) {
        return bookingQueryService.getAdminBookingDetail(bookingId);
    }

    @Transactional
    public void rejectAllPendingBookingsForMentor(UUID mentorUserId, String reason) {
        bookingLifecycleMaintenanceService.rejectAllPendingBookingsForMentor(mentorUserId, reason);
    }

    @Transactional
    public int expireStalePendingBookings() {
        return bookingLifecycleMaintenanceService.expireStalePendingBookings();
    }

    @Transactional
    public int expireAwaitingPaymentBookings() {
        return bookingLifecycleMaintenanceService.expireAwaitingPaymentBookings();
    }

    @Transactional
    public int processPostSessionLifecycle() {
        return bookingLifecycleMaintenanceService.processPostSessionLifecycle();
    }

    // Helper method for unit tests calling private toBookingResponse via reflection
    private BookingResponse toBookingResponse(com.fptu.exe.skillswap.modules.booking.domain.Booking booking) {
        return bookingResponseMapper.toBookingResponse(booking);
    }
}
