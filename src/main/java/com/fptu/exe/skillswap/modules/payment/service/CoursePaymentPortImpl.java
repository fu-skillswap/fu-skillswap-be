package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.payment.domain.CreditOriginType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerSourceType;
import com.fptu.exe.skillswap.modules.payment.port.CoursePaymentPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Internal adapter from the public course-payment contract to Payment's ledger and settlement model. */
@Service
@RequiredArgsConstructor
class CoursePaymentPortImpl implements CoursePaymentPort {

    private final CreditLedgerService creditLedgerService;
    private final SettlementService settlementService;
    private final PaymentProperties paymentProperties;

    @Override
    @Transactional(readOnly = true)
    public CoursePaymentQuote quoteEnrollment(int basePriceScoin) {
        int basePrice = Math.max(0, basePriceScoin);
        int buyerFee = PricingPolicy.bpsAmount(basePrice, paymentProperties.getCourseBuyerFeeBps());
        int mentorCommission = PricingPolicy.bpsAmount(basePrice, paymentProperties.getCourseMentorCommissionBps());
        return new CoursePaymentQuote(
                basePrice,
                buyerFee,
                Math.addExact(basePrice, buyerFee),
                mentorCommission,
                Math.max(0, basePrice - mentorCommission)
        );
    }

    @Override
    @Transactional
    public void collectEnrollment(CourseEnrollmentCollection command) {
        creditLedgerService.reserveCredit(
                command.studentUserId(), command.amountScoin(), LedgerSourceType.COURSE_ENROLLMENT,
                command.enrollmentId(), List.of(CreditOriginType.values()),
                "Course Enrollment Reservation: " + command.courseTitle());
        creditLedgerService.consumeReservedCredit(
                command.studentUserId(), LedgerSourceType.COURSE_ENROLLMENT, command.enrollmentId(),
                "Course Enrollment Deduction: " + command.courseTitle());
    }

    @Override
    @Transactional
    public void releaseAllocation(CourseAllocationRelease command) {
        settlementService.releaseCourseAllocation(
                command.mentorUserId(), command.allocationId(), command.mentorPayoutScoin(),
                command.platformRevenueScoin(), command.basePriceScoin(), command.buyerFeeScoin(),
                command.mentorCommissionScoin(), command.operationKey());
    }

    @Override
    @Transactional
    public void refundEnrollment(CourseEnrollmentRefund command) {
        creditLedgerService.refundCredit(
                command.studentUserId(), CreditOriginType.REFUND, LedgerSourceType.COURSE_ENROLLMENT,
                command.enrollmentId(), command.amountScoin(), command.memo(), command.operationKey());
    }
}
