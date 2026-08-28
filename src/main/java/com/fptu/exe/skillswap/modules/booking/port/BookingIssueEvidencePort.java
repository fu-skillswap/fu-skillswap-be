package com.fptu.exe.skillswap.modules.booking.port;

import com.fptu.exe.skillswap.modules.booking.dto.response.BookingIssueDetailResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingIssueEvidenceDownloadResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingIssueEvidenceResponse;

import java.util.UUID;

/**
 * Public admin-facing contract for reading and moderating booking dispute evidence.
 * The Booking implementation remains private to its module.
 */
public interface BookingIssueEvidencePort {

    BookingIssueDetailResponse getForAdmin(UUID bookingId);

    BookingIssueEvidenceDownloadResponse downloadForAdmin(UUID bookingId, UUID evidenceId);

    BookingIssueEvidenceResponse setAdminVisibility(UUID bookingId, UUID evidenceId,
                                                     UUID adminUserId, boolean hidden, String reason);
}
