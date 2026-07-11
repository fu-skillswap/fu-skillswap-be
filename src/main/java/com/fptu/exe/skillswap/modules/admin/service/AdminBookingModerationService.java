package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminBookingListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminResolveBookingIssueRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.RespondBookingRescheduleRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingRescheduleRequestResponse;
import com.fptu.exe.skillswap.modules.booking.service.BookingRescheduleService;
import com.fptu.exe.skillswap.modules.booking.service.BookingService;
import com.fptu.exe.skillswap.modules.booking.domain.BookingRescheduleStatus;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminBookingModerationService {

    private final BookingService bookingService;
    private final BookingRescheduleService bookingRescheduleService;
    private final AdminAuditWriterService adminAuditWriterService;

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> getBookings(AdminBookingListRequest request) {
        return bookingService.getAdminBookings(request);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingDetail(UUID bookingId) {
        return bookingService.getAdminBookingDetail(bookingId);
    }

    @Transactional
    public BookingResponse resolveBookingIssue(UUID adminUserId, UUID bookingId, AdminResolveBookingIssueRequest request) {
        // Resolve the booking issue and get the new status
        BookingResponse booking = bookingService.getAdminBookingDetail(bookingId);
        String oldStatus = booking.status().name();

        BookingResponse response = bookingService.resolveBookingIssue(adminUserId, bookingId, request);

        // Save Audit Log
        adminAuditWriterService.writeOperatorEvent(
                adminUserId,
                "BOOKING",
                bookingId,
                "RESOLVE_BOOKING_ISSUE",
                Map.of("status", oldStatus),
                Map.of("status", response.status().name(), "reason", request.adminNote() == null ? "" : request.adminNote(), "action", request.action().name())
        );

        return response;
    }

    @Transactional(readOnly = true)
    public List<BookingRescheduleRequestResponse> getRescheduleRequests(UUID bookingId) {
        return bookingRescheduleService.getAdminBookingRequests(bookingId);
    }

    @Transactional
    public BookingRescheduleRequestResponse acceptRescheduleRequest(UUID adminUserId, UUID requestId, RespondBookingRescheduleRequest request) {
        BookingRescheduleRequestResponse response = bookingRescheduleService.acceptByAdmin(adminUserId, requestId, request);

        adminAuditWriterService.writeOperatorEvent(
                adminUserId,
                "BOOKING_RESCHEDULE_REQUEST",
                requestId,
                "APPROVE_RESCHEDULE_REQUEST",
                Map.of(
                        "status", BookingRescheduleStatus.PENDING.name(),
                        "bookingId", response.bookingId().toString(),
                        "from", response.previousSelectedStartTime() == null ? "" : response.previousSelectedStartTime().toString(),
                        "to", response.proposedSelectedStartTime() == null ? "" : response.proposedSelectedStartTime().toString()
                ),
                Map.of("status", BookingRescheduleStatus.ACCEPTED.name(), "reason", request == null || request.reason() == null ? "" : request.reason(), "adminOverride", true)
        );

        return response;
    }

    @Transactional
    public BookingRescheduleRequestResponse rejectRescheduleRequest(UUID adminUserId, UUID requestId, RespondBookingRescheduleRequest request) {
        BookingRescheduleRequestResponse response = bookingRescheduleService.rejectByAdmin(adminUserId, requestId, request);

        adminAuditWriterService.writeOperatorEvent(
                adminUserId,
                "BOOKING_RESCHEDULE_REQUEST",
                requestId,
                "REJECT_RESCHEDULE_REQUEST",
                Map.of(
                        "status", BookingRescheduleStatus.PENDING.name(),
                        "bookingId", response.bookingId().toString(),
                        "from", response.previousSelectedStartTime() == null ? "" : response.previousSelectedStartTime().toString(),
                        "to", response.proposedSelectedStartTime() == null ? "" : response.proposedSelectedStartTime().toString()
                ),
                Map.of("status", BookingRescheduleStatus.REJECTED.name(), "reason", request == null || request.reason() == null ? "" : request.reason(), "adminOverride", true)
        );

        return response;
    }
}
