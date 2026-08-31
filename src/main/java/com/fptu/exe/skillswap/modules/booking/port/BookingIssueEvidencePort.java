package com.fptu.exe.skillswap.modules.booking.port;

import java.util.UUID;

/**
 * Public admin-facing contract for reading and moderating booking dispute evidence.
 * The Booking implementation remains private to its module.
 */
public interface BookingIssueEvidencePort {

    BookingIssueDetailView getForAdmin(UUID bookingId);

    BookingIssueEvidenceDownloadView downloadForAdmin(UUID bookingId, UUID evidenceId);

    BookingIssueEvidenceView setAdminVisibility(UUID bookingId, UUID evidenceId,
                                                UUID adminUserId, boolean hidden, String reason);
}
