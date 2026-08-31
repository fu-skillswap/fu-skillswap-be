package com.fptu.exe.skillswap.modules.course.service;

import com.fptu.exe.skillswap.modules.course.domain.CourseEnrollment;
import com.fptu.exe.skillswap.modules.course.domain.CourseEnrollmentSettlement;
import com.fptu.exe.skillswap.modules.course.domain.CourseSettlementStatus;
import com.fptu.exe.skillswap.modules.course.domain.EnrollmentStatus;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentSettlementRepository;
import com.fptu.exe.skillswap.modules.course.event.CourseEnrollmentEndedEvent;
import com.fptu.exe.skillswap.modules.payment.port.CoursePaymentPort;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Owns self-paced course escrow allocations and their hold-period settlement transitions. */
@Service
@RequiredArgsConstructor
@Slf4j
public class CourseSettlementService {

    private static final int HOLD_PERIOD_DAYS = 7;

    private final CourseEnrollmentSettlementRepository settlementRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final CoursePaymentPort coursePaymentPort;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void generateSettlements(CourseEnrollment enrollment) {
        CourseEnrollment lockedEnrollment = enrollmentRepository.findByIdForUpdate(enrollment.getId())
                .orElseThrow(() -> new IllegalArgumentException("Enrollment not found"));

        Optional<CourseEnrollmentSettlement> existing = settlementRepository.findByEnrollmentId(lockedEnrollment.getId());
        if (existing.isPresent()) {
            return;
        }

        int basePrice = lockedEnrollment.getBasePriceScoin();
        int buyerFee = lockedEnrollment.getBuyerFeeScoin();
        int mentorCommission = lockedEnrollment.getMentorCommissionScoin();
        int mentorPayout = lockedEnrollment.getMentorPayoutScoin();
        int platformRevenue = Math.addExact(buyerFee, mentorCommission);

        Instant eligibleAt = Instant.now().plus(HOLD_PERIOD_DAYS, ChronoUnit.DAYS);

        CourseEnrollmentSettlement settlement = CourseEnrollmentSettlement.builder()
                .enrollment(lockedEnrollment)
                .basePriceScoin(basePrice)
                .buyerFeeScoin(buyerFee)
                .mentorCommissionScoin(mentorCommission)
                .mentorPayoutScoin(mentorPayout)
                .platformRevenueScoin(platformRevenue)
                .studentRefundableScoin(basePrice)
                .status(CourseSettlementStatus.HELD)
                .eligibleAt(eligibleAt)
                .build();

        settlementRepository.save(settlement);
    }

    @Transactional
    public int markEligibleSettlements() {
        Instant now = Instant.now();
        List<CourseEnrollmentSettlement> heldSettlements = settlementRepository.findByStatus(CourseSettlementStatus.HELD);
        int changed = 0;
        for (CourseEnrollmentSettlement settlement : heldSettlements) {
            if (settlement.getEligibleAt() != null && !settlement.getEligibleAt().isAfter(now)) {
                settlement.setStatus(CourseSettlementStatus.ELIGIBLE);
                changed++;
            }
        }
        return changed;
    }

    @Transactional
    public boolean releaseEligibleAllocation(UUID allocationId, Instant now) {
        CourseEnrollmentSettlement allocation = settlementRepository.findByIdForUpdate(allocationId).orElse(null);
        if (allocation == null || allocation.getStatus() != CourseSettlementStatus.ELIGIBLE
                || allocation.getEligibleAt() == null || allocation.getEligibleAt().isAfter(now)) {
            return false;
        }
        CourseEnrollment enrollment = enrollmentRepository.findByIdForUpdate(allocation.getEnrollment().getId()).orElseThrow();
        if (enrollment.getStatus() != EnrollmentStatus.ACTIVE && enrollment.getStatus() != EnrollmentStatus.COMPLETED) {
            return false;
        }
        String operationKey = "COURSE_SETTLEMENT_RELEASE:" + allocation.getId();
        coursePaymentPort.releaseAllocation(new CoursePaymentPort.CourseAllocationRelease(
                enrollment.getCourse().getMentorProfile().getUserId(),
                allocation.getId(),
                allocation.getMentorPayoutScoin(),
                allocation.getPlatformRevenueScoin(),
                allocation.getBasePriceScoin(),
                allocation.getBuyerFeeScoin(),
                allocation.getMentorCommissionScoin(), operationKey));
        allocation.setStatus(CourseSettlementStatus.RELEASED);
        allocation.setReleasedAt(now);
        allocation.setReleaseOperationKey(operationKey);
        return true;
    }

    @Transactional
    public int refundUnreleasedAllocations(UUID enrollmentId, String reason) {
        CourseEnrollment enrollment = enrollmentRepository.findByIdForUpdate(enrollmentId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Enrollment not found"));
        CourseEnrollmentSettlement allocation = settlementRepository.findByEnrollmentIdForUpdate(enrollmentId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_CONFLICT, "Enrollment chưa có settlement allocation"));
        
        if (allocation.getStatus() != CourseSettlementStatus.HELD && allocation.getStatus() != CourseSettlementStatus.ELIGIBLE) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Không thể hoàn tiền cho khoản thanh toán đã release hoặc đã refund");
        }

        int refundable = allocation.getStudentRefundableScoin();
        String operationKey = "COURSE_REFUND:" + enrollmentId;
        coursePaymentPort.refundEnrollment(new CoursePaymentPort.CourseEnrollmentRefund(
                enrollment.getStudentUserId(), enrollmentId, refundable,
                "Course refund: " + enrollmentId, operationKey));

        allocation.setStatus(CourseSettlementStatus.REFUNDED);
        allocation.setRefundedAt(Instant.now());
        allocation.setRefundReason(reason == null ? "LEARNER_REFUND" : reason);
        allocation.setRefundOperationKey(operationKey + ":" + allocation.getId());

        enrollment.setStatus(EnrollmentStatus.REFUNDED);
        if (enrollment.getCourse() != null) {
            eventPublisher.publishEvent(new CourseEnrollmentEndedEvent(
                    UUID.randomUUID(), enrollmentId, enrollment.getCourse().getId(), enrollment.getStudentUserId()));
        }
        return refundable;
    }

    @Transactional(readOnly = true)
    public List<UUID> findEligibleAllocationIdsBefore(Instant eligibleAt) {
        return settlementRepository.findTop100ByStatusAndEligibleAtBeforeOrderByEligibleAtAsc(
                        CourseSettlementStatus.ELIGIBLE, eligibleAt)
                .stream()
                .map(CourseEnrollmentSettlement::getId)
                .toList();
    }
}
