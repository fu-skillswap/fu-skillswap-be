package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminBookingListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminResolveBookingIssueRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminReverseResolutionRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.port.BookingAdminPort;
import com.fptu.exe.skillswap.modules.booking.port.dto.BookingAdminFilterQuery;
import com.fptu.exe.skillswap.modules.booking.port.dto.BookingAdminResolveIssueCommand;
import com.fptu.exe.skillswap.modules.booking.port.dto.BookingAdminReverseResolutionCommand;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminBookingModerationService {

    private final BookingAdminPort bookingService;
    private final AdminAuditWriterService adminAuditWriterService;

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> getBookings(AdminBookingListRequest request) {
        BookingAdminFilterQuery query = new BookingAdminFilterQuery();
        if (request != null) {
            query.setStatus(request.getStatus());
            query.setMentorUserId(request.getMentorUserId());
            query.setMenteeUserId(request.getMenteeUserId());
            query.setPage(request.getPage());
            query.setSize(request.getSize());
            query.setSortBy(request.getSortBy());
            query.setDirection(request.getDirection());
        }
        return bookingService.getAdminBookings(query);
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

        BookingAdminResolveIssueCommand command = new BookingAdminResolveIssueCommand(
                request.action(),
                request.reasonCode(),
                request.adminNote(),
                request.menteeBps(),
                request.mentorBps(),
                request.platformBps()
        );
        BookingResponse response = bookingService.resolveBookingIssue(adminUserId, bookingId, command);

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

    @Transactional
    public BookingResponse reverseBookingIssueResolution(UUID adminUserId, UUID bookingId, AdminReverseResolutionRequest request) {
        BookingResponse booking = bookingService.getAdminBookingDetail(bookingId);
        String oldStatus = booking.status().name();

        BookingAdminReverseResolutionCommand command = new BookingAdminReverseResolutionCommand(
                request.reasonCode(),
                request.adminNote()
        );
        BookingResponse response = bookingService.reverseBookingIssueResolution(adminUserId, bookingId, command);

        // Save Audit Log
        adminAuditWriterService.writeOperatorEvent(
                adminUserId,
                "BOOKING",
                bookingId,
                "REVERSE_BOOKING_ISSUE_RESOLUTION",
                Map.of("status", oldStatus),
                Map.of("status", response.status().name(), "reason", request.adminNote() == null ? "" : request.adminNote(), "reasonCode", request.reasonCode().name())
        );

        return response;
    }

}
