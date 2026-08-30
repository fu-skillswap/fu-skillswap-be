package com.fptu.exe.skillswap.modules.booking.port;

import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.port.dto.BookingAdminFilterQuery;
import com.fptu.exe.skillswap.modules.booking.port.dto.BookingAdminResolveIssueCommand;
import com.fptu.exe.skillswap.modules.booking.port.dto.BookingAdminReverseResolutionCommand;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;

import java.util.UUID;

/** Admin operations exposed by the Booking module without leaking its service facade. */
public interface BookingAdminPort {

    PageResponse<BookingResponse> getAdminBookings(BookingAdminFilterQuery query);

    BookingResponse getAdminBookingDetail(UUID bookingId);

    BookingResponse resolveBookingIssue(UUID adminUserId, UUID bookingId, BookingAdminResolveIssueCommand command);

    BookingResponse reverseBookingIssueResolution(UUID adminUserId, UUID bookingId, BookingAdminReverseResolutionCommand command);
}

