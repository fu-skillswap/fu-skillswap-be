package com.fptu.exe.skillswap.modules.booking.port;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminBookingListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminResolveBookingIssueRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminReverseResolutionRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;

import java.util.UUID;

/** Admin operations exposed by the Booking module without leaking its service facade. */
public interface BookingAdminPort {

    PageResponse<BookingResponse> getAdminBookings(AdminBookingListRequest request);

    BookingResponse getAdminBookingDetail(UUID bookingId);

    BookingResponse resolveBookingIssue(UUID adminUserId, UUID bookingId, AdminResolveBookingIssueRequest request);

    BookingResponse reverseBookingIssueResolution(UUID adminUserId, UUID bookingId, AdminReverseResolutionRequest request);
}
