package com.fptu.exe.skillswap.modules.course.service;

import com.fptu.exe.skillswap.modules.course.domain.CourseEnrollment;
import com.fptu.exe.skillswap.modules.course.domain.CourseEnrollmentSettlement;
import com.fptu.exe.skillswap.modules.course.domain.CourseSettlementStatus;
import com.fptu.exe.skillswap.modules.course.domain.CourseSession;
import com.fptu.exe.skillswap.modules.course.domain.CourseSessionStatus;
import com.fptu.exe.skillswap.modules.course.domain.EnrollmentStatus;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentSettlementRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseSessionRepository;
import com.fptu.exe.skillswap.modules.payment.domain.CreditOriginType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerSourceType;
import com.fptu.exe.skillswap.modules.payment.service.CreditLedgerService;
import com.fptu.exe.skillswap.modules.payment.service.SettlementService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Owns immutable per-session course escrow allocations and their local state transitions. */
@Service
@RequiredArgsConstructor
@Slf4j
public class CourseSettlementService {

    private final CourseEnrollmentSettlementRepository settlementRepository;
    private final CourseSessionRepository sessionRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final CreditLedgerService creditLedgerService;
    private final SettlementService settlementService;

    @Transactional
    public void generateSettlements(CourseEnrollment enrollment) {
        CourseEnrollment lockedEnrollment = enrollmentRepository.findByIdForUpdate(enrollment.getId())
                .orElseThrow(() -> new IllegalArgumentException("Enrollment not found"));
        List<CourseSession> sessions = sessionRepository
                .findByCourseIdOrderByScheduledStartAtAsc(lockedEnrollment.getCourse().getId());
        if (sessions.isEmpty()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Course phải có session trước khi nhận thanh toán");
        }

        Set<UUID> existingSessionIds = settlementRepository.findByEnrollmentId(lockedEnrollment.getId()).stream()
                .map(settlement -> settlement.getCourseSession().getId())
                .collect(Collectors.toSet());
        if (!existingSessionIds.isEmpty() && existingSessionIds.size() != sessions.size()) {
            throw new IllegalStateException("Course settlement allocation is incomplete and requires financial review");
        }
        if (existingSessionIds.size() == sessions.size()) {
            return;
        }

        int count = sessions.size();
        for (int index = 0; index < count; index++) {
            CourseSession session = sessions.get(index);
            int basePrice = allocate(lockedEnrollment.getBasePriceScoin(), count, index);
            int buyerFee = allocate(lockedEnrollment.getBuyerFeeScoin(), count, index);
            int mentorCommission = allocate(lockedEnrollment.getMentorCommissionScoin(), count, index);
            CourseEnrollmentSettlement allocation = CourseEnrollmentSettlement.builder()
                    .enrollment(lockedEnrollment)
                    .courseSession(session)
                    .basePriceScoin(basePrice)
                    .buyerFeeScoin(buyerFee)
                    .mentorCommissionScoin(mentorCommission)
                    .mentorPayoutScoin(allocate(lockedEnrollment.getMentorPayoutScoin(), count, index))
                    // The per-session platform revenue must equal the two fee allocations exactly.
                    .platformRevenueScoin(Math.addExact(buyerFee, mentorCommission))
                    // Learner voluntary refunds return only unused base-price allocations.
                    .studentRefundableScoin(basePrice)
                    .status(CourseSettlementStatus.HELD)
                    .build();
            settlementRepository.save(allocation);
        }
    }

    @Transactional
    public int markCompletedSessionsEligible() {
        int changed = 0;
        List<UUID> sessionIds = sessionRepository.findCompletedSessionIdsWithAllocationStatus(
                CourseSessionStatus.COMPLETED,
                CourseSettlementStatus.HELD,
                PageRequest.of(0, 100));
        Instant now = Instant.now();
        for (UUID sessionId : sessionIds) {
            changed += markSessionEligible(sessionId, now);
        }
        return changed;
    }

    @Transactional
    public int markSessionEligible(UUID sessionId, Instant now) {
        int changed = 0;
        for (CourseEnrollmentSettlement allocation : settlementRepository.findByCourseSessionIdForUpdate(sessionId)) {
            if (allocation.getStatus() != CourseSettlementStatus.HELD) {
                continue;
            }
            EnrollmentStatus enrollmentStatus = allocation.getEnrollment().getStatus();
            if (enrollmentStatus == EnrollmentStatus.ACTIVE || enrollmentStatus == EnrollmentStatus.COMPLETED) {
                allocation.setStatus(CourseSettlementStatus.ELIGIBLE);
                allocation.setEligibleAt(now);
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
        settlementService.releaseCourseAllocation(
                enrollment.getCourse().getMentorProfile().getUserId(),
                allocation.getId(),
                allocation.getMentorPayoutScoin(),
                allocation.getPlatformRevenueScoin(),
                allocation.getBasePriceScoin(),
                allocation.getBuyerFeeScoin(),
                allocation.getMentorCommissionScoin(),
                operationKey
        );
        allocation.setStatus(CourseSettlementStatus.RELEASED);
        allocation.setReleasedAt(now);
        allocation.setReleaseOperationKey(operationKey);
        return true;
    }

    /** Internal policy operation. A future API/admin workflow may invoke it after authorization. */
    @Transactional
    public int refundUnreleasedAllocations(UUID enrollmentId, String reason) {
        CourseEnrollment enrollment = enrollmentRepository.findByIdForUpdate(enrollmentId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Enrollment not found"));
        List<CourseEnrollmentSettlement> allocations = settlementRepository.findByEnrollmentIdForUpdate(enrollmentId);
        if (allocations.isEmpty()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Enrollment chưa có session allocation");
        }
        int refundable = allocations.stream()
                .filter(allocation -> allocation.getStatus() == CourseSettlementStatus.HELD)
                .mapToInt(CourseEnrollmentSettlement::getStudentRefundableScoin)
                .sum();
        if (refundable == 0) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Không còn session chưa release để hoàn tiền");
        }
        String operationKey = "COURSE_REFUND:" + enrollmentId;
        creditLedgerService.refundCredit(
                enrollment.getStudentUserId(), CreditOriginType.REFUND, LedgerSourceType.COURSE_ENROLLMENT,
                enrollmentId, refundable, "Course refund for unreleased sessions: " + enrollmentId, operationKey);
        for (CourseEnrollmentSettlement allocation : allocations) {
            if (allocation.getStatus() == CourseSettlementStatus.HELD) {
                allocation.setStatus(CourseSettlementStatus.REFUNDED);
                allocation.setRefundedAt(Instant.now());
                allocation.setRefundReason(reason == null ? "LEARNER_PARTIAL_REFUND" : reason);
                allocation.setRefundOperationKey(operationKey + ":" + allocation.getId());
            }
        }
        boolean hasReleased = allocations.stream().anyMatch(allocation -> allocation.getStatus() == CourseSettlementStatus.RELEASED);
        enrollment.setStatus(hasReleased ? EnrollmentStatus.PARTIAL_REFUNDED : EnrollmentStatus.REFUNDED);
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

    private int allocate(int total, int count, int index) {
        int base = total / count;
        return index == count - 1 ? base + (total % count) : base;
    }
}
