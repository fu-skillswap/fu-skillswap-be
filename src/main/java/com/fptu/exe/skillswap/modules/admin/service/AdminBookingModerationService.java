package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.booking.port.BookingAdminPort;
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
    public PageResponse<Map<String, Object>> getBookings(BookingAdminPort.AdminBookingQuery request) {
        return bookingService.getAdminBookings(request);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getBookingDetail(UUID bookingId) {
        return bookingService.getAdminBookingDetail(bookingId);
    }

    @Transactional
    public Map<String, Object> resolveBookingIssue(UUID adminUserId, UUID bookingId, BookingAdminPort.ResolveBookingIssueCommand request) {
        // Resolve the booking issue and get the new status
        Map<String, Object> booking = bookingService.getAdminBookingDetail(bookingId);
        String oldStatus = String.valueOf(booking.get("status"));

        Map<String, Object> response = bookingService.resolveBookingIssue(adminUserId, bookingId, request);

        // Save Audit Log
        adminAuditWriterService.writeOperatorEvent(
                adminUserId,
                "BOOKING",
                bookingId,
                "RESOLVE_BOOKING_ISSUE",
                Map.of("status", oldStatus),
                Map.of("status", String.valueOf(response.get("status")), "reason", request.adminNote() == null ? "" : request.adminNote(), "action", request.action())
        );

        return response;
    }

    @Transactional
    public Map<String, Object> reverseBookingIssueResolution(UUID adminUserId, UUID bookingId, BookingAdminPort.ReverseBookingIssueResolutionCommand request) {
        Map<String, Object> booking = bookingService.getAdminBookingDetail(bookingId);
        String oldStatus = String.valueOf(booking.get("status"));

        Map<String, Object> response = bookingService.reverseBookingIssueResolution(adminUserId, bookingId, request);

        // Save Audit Log
        adminAuditWriterService.writeOperatorEvent(
                adminUserId,
                "BOOKING",
                bookingId,
                "REVERSE_BOOKING_ISSUE_RESOLUTION",
                Map.of("status", oldStatus),
                Map.of("status", String.valueOf(response.get("status")), "reason", request.adminNote() == null ? "" : request.adminNote(), "reasonCode", request.reasonCode())
        );

        return response;
    }

}
