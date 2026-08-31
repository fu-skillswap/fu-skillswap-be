package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.dto.request.AcceptBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.AdminBookingListRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.AdminResolveBookingIssueRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.AdminReverseResolutionRequest;
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
import com.fptu.exe.skillswap.modules.booking.port.BookingAdminPort;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final SessionAttendanceService sessionAttendanceService;
    private final BookingQueryService bookingQueryService;
    private final BookingLifecycleMaintenanceService bookingLifecycleMaintenanceService;
    private final BookingResponseMapper bookingResponseMapper;

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
    public BookingResponse checkIn(UUID currentUserId, UUID bookingId) {
        Booking booking = sessionAttendanceService.checkIn(currentUserId, bookingId);
        return bookingResponseMapper.toBookingResponse(booking);
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
    public BookingResponse reverseBookingIssueResolution(UUID adminUserId, UUID bookingId, AdminReverseResolutionRequest request) {
        return bookingCompletionService.reverseBookingIssueResolution(adminUserId, bookingId, request);
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
