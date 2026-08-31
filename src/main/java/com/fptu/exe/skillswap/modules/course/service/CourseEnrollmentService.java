package com.fptu.exe.skillswap.modules.course.service;

import com.fptu.exe.skillswap.modules.course.domain.Course;
import com.fptu.exe.skillswap.modules.course.domain.CourseEnrollment;
import com.fptu.exe.skillswap.modules.course.domain.EnrollmentStatus;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseRepository;
import com.fptu.exe.skillswap.modules.course.event.CourseEnrollmentActivatedEvent;
import com.fptu.exe.skillswap.modules.payment.port.CoursePaymentPort;
import com.fptu.exe.skillswap.shared.util.UuidUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseEnrollmentService {

    private final CourseRepository courseRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final CoursePaymentPort coursePaymentPort;
    private final CourseSettlementService settlementService;
    private final ApplicationEventPublisher eventPublisher;

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
        CoursePaymentPort.CoursePaymentQuote quote = coursePaymentPort.quoteEnrollment(course.getPriceScoin());

        UUID enrollmentId = UuidUtil.generateUuidV7();
        coursePaymentPort.collectEnrollment(new CoursePaymentPort.CourseEnrollmentCollection(
                studentUserId, enrollmentId, quote.paidAmountScoin(), course.getTitle()));

        CourseEnrollment enrollment = CourseEnrollment.builder()
                .id(enrollmentId)
                .course(course)
                .studentUserId(studentUserId)
                .basePriceScoin(quote.basePriceScoin())
                .buyerFeeScoin(quote.buyerFeeScoin())
                .paidAmountScoin(quote.paidAmountScoin())
                .mentorCommissionScoin(quote.mentorCommissionScoin())
                .mentorPayoutScoin(quote.mentorPayoutScoin())
                .status(EnrollmentStatus.ACTIVE)
                .build();

        enrollment = enrollmentRepository.save(enrollment);

        eventPublisher.publishEvent(new CourseEnrollmentActivatedEvent(
                UUID.randomUUID(), enrollment.getId(), course.getId(), studentUserId));

        // Generate hold-period settlement
        settlementService.generateSettlements(enrollment);

        return enrollment;
    }
}
