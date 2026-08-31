package com.fptu.exe.skillswap.modules.payment.port;

import java.util.UUID;

/**
 * Course-specific financial capability exposed by Payment.
 *
 * <p>The contract deliberately carries only immutable values and identifiers. Course must not
 * depend on payment ledger, settlement, or pricing implementation types.</p>
 */
public interface CoursePaymentPort {

    CoursePaymentQuote quoteEnrollment(int basePriceScoin);

    void collectEnrollment(CourseEnrollmentCollection command);

    void releaseAllocation(CourseAllocationRelease command);

    void refundEnrollment(CourseEnrollmentRefund command);

    record CoursePaymentQuote(
            int basePriceScoin,
            int buyerFeeScoin,
            int paidAmountScoin,
            int mentorCommissionScoin,
            int mentorPayoutScoin
    ) {
    }

    record CourseEnrollmentCollection(UUID studentUserId, UUID enrollmentId, int amountScoin, String courseTitle) {
    }

    record CourseAllocationRelease(
            UUID mentorUserId,
            UUID allocationId,
            int mentorPayoutScoin,
            int platformRevenueScoin,
            int basePriceScoin,
            int buyerFeeScoin,
            int mentorCommissionScoin,
            String operationKey
    ) {
    }

    record CourseEnrollmentRefund(
            UUID studentUserId,
            UUID enrollmentId,
            int amountScoin,
            String memo,
            String operationKey
    ) {
    }
}
