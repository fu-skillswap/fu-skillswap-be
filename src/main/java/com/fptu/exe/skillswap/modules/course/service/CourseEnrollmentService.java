package com.fptu.exe.skillswap.modules.course.service;

import com.fptu.exe.skillswap.modules.course.domain.Course;
import com.fptu.exe.skillswap.modules.course.domain.CourseEnrollment;
import com.fptu.exe.skillswap.modules.course.domain.EnrollmentStatus;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseRepository;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerSourceType;
import com.fptu.exe.skillswap.modules.payment.domain.CreditOriginType;
import com.fptu.exe.skillswap.modules.payment.service.CreditLedgerService;
import com.fptu.exe.skillswap.shared.util.UuidUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseEnrollmentService {

    private final CourseRepository courseRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final CreditLedgerService creditLedgerService;
    private final CourseSettlementService settlementService;

    // Hardcoded commission policy for now (Buyer 10%, Mentor 5% platform fee)
    private static final double BUYER_FEE_RATE = 0.10;
    private static final double MENTOR_FEE_RATE = 0.05;

    @Transactional
    public CourseEnrollment enrollStudent(UUID studentUserId, UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));

        if (enrollmentRepository.existsByCourseIdAndStudentUserId(courseId, studentUserId)) {
            throw new IllegalStateException("Student already enrolled");
        }

        // Increment enrolled count (unlimited capacity for self-paced)
        courseRepository.incrementEnrolledCount(courseId);

        // Snapshot pricing
        int basePrice = course.getPriceScoin();
        int buyerFee = (int) Math.round(basePrice * BUYER_FEE_RATE);
        int totalPaid = basePrice + buyerFee;
        
        int platformRevenueFromMentor = (int) Math.round(basePrice * MENTOR_FEE_RATE);
        int mentorPayout = basePrice - platformRevenueFromMentor;

        UUID enrollmentId = UuidUtil.generateUuidV7();
        creditLedgerService.reserveCredit(
            studentUserId, 
            totalPaid, 
            LedgerSourceType.COURSE_ENROLLMENT, 
            enrollmentId,
            java.util.List.of(CreditOriginType.values()), // All origins allowed
            "Course Enrollment Reservation: " + course.getTitle()
        );
        creditLedgerService.consumeReservedCredit(
            studentUserId,
            LedgerSourceType.COURSE_ENROLLMENT,
            enrollmentId,
            "Course Enrollment Deduction: " + course.getTitle()
        );

        CourseEnrollment enrollment = CourseEnrollment.builder()
                .id(enrollmentId)
                .course(course)
                .studentUserId(studentUserId)
                .basePriceScoin(basePrice)
                .buyerFeeScoin(buyerFee)
                .paidAmountScoin(totalPaid)
                .mentorCommissionScoin(platformRevenueFromMentor)
                .mentorPayoutScoin(mentorPayout)
                .status(EnrollmentStatus.ACTIVE)
                .build();

        enrollment = enrollmentRepository.save(enrollment);

        // Generate hold-period settlement
        settlementService.generateSettlements(enrollment);

        return enrollment;
    }
}
