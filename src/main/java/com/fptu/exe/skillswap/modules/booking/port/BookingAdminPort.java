package com.fptu.exe.skillswap.modules.booking.port;

import com.fptu.exe.skillswap.shared.dto.response.PageResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/** Admin operations exposed by the Booking module without leaking its service facade. */
public interface BookingAdminPort {

    PageResponse<Map<String, Object>> getAdminBookings(AdminBookingQuery query);
    Map<String, Object> getAdminBookingDetail(UUID bookingId);
    Map<String, Object> resolveBookingIssue(UUID adminUserId, UUID bookingId, ResolveBookingIssueCommand command);
    Map<String, Object> reverseBookingIssueResolution(UUID adminUserId, UUID bookingId, ReverseBookingIssueResolutionCommand command);
    List<String> bookingStatusNames();

    @Getter @Setter
    class AdminBookingQuery {
        private String status;
        private UUID mentorUserId;
        private UUID menteeUserId;
        private int page;
        private int size = 20;
        private String sortBy = "selectedStartTime";
        private String direction = "DESC";
    }

    record ResolveBookingIssueCommand(@NotNull String action, @NotNull String reasonCode, String adminNote,
                                      @Min(0) @Max(10000) Integer menteeBps, @Min(0) @Max(10000) Integer mentorBps,
                                      @Min(0) @Max(10000) Integer platformBps) { }
    record ReverseBookingIssueResolutionCommand(@NotBlank String reasonCode, @NotBlank String adminNote) { }
}
