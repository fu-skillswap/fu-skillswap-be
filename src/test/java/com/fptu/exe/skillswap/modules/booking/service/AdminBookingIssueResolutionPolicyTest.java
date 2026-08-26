package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminResolveBookingIssueRequest;
import com.fptu.exe.skillswap.modules.booking.domain.AdminBookingIssueResolutionAction;
import com.fptu.exe.skillswap.modules.booking.domain.AdminBookingIssueResolutionReasonCode;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueType;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminBookingIssueResolutionPolicyTest {

    @Test
    void partialSettlement_acceptsBalancedAllocationForQualityDispute() {
        assertDoesNotThrow(() -> AdminBookingIssueResolutionPolicy.validate(new AdminResolveBookingIssueRequest(
                AdminBookingIssueResolutionAction.PARTIAL_SETTLEMENT,
                AdminBookingIssueResolutionReasonCode.QUALITY_PARTIAL_COMPENSATION,
                "Bồi hoàn một phần theo minh chứng hai bên.", 5000, 3500, 1500
        ), BookingIssueType.QUALITY_ISSUE));
    }

    @Test
    void partialSettlement_rejectsUnbalancedAllocation() {
        BaseException error = assertThrows(BaseException.class, () -> AdminBookingIssueResolutionPolicy.validate(
                new AdminResolveBookingIssueRequest(AdminBookingIssueResolutionAction.PARTIAL_SETTLEMENT,
                        AdminBookingIssueResolutionReasonCode.QUALITY_PARTIAL_COMPENSATION,
                        "Bồi hoàn một phần.", 5000, 3000, 1500),
                BookingIssueType.QUALITY_ISSUE));
        assertEquals(ErrorCode.BAD_REQUEST, error.getErrorCode());
    }

    @Test
    void partialSettlement_rejectsNoShowDispute() {
        BaseException error = assertThrows(BaseException.class, () -> AdminBookingIssueResolutionPolicy.validate(
                new AdminResolveBookingIssueResolutionRequestBuilder().partial(), BookingIssueType.MENTOR_NO_SHOW));
        assertEquals(ErrorCode.BAD_REQUEST, error.getErrorCode());
    }

    @Test
    void otherReason_requiresAdminNote() {
        BaseException error = assertThrows(BaseException.class, () -> AdminBookingIssueResolutionPolicy.validate(
                new AdminResolveBookingIssueRequest(AdminBookingIssueResolutionAction.RELEASE_AS_IS,
                        AdminBookingIssueResolutionReasonCode.OTHER, null, null, null, null), BookingIssueType.OTHER));
        assertEquals(ErrorCode.BAD_REQUEST, error.getErrorCode());
    }

    private static final class AdminResolveBookingIssueResolutionRequestBuilder {
        private AdminResolveBookingIssueRequest partial() {
            return new AdminResolveBookingIssueRequest(AdminBookingIssueResolutionAction.PARTIAL_SETTLEMENT,
                    AdminBookingIssueResolutionReasonCode.QUALITY_PARTIAL_COMPENSATION,
                    "Bồi hoàn một phần.", 5000, 3500, 1500);
        }
    }
}
