package com.fptu.exe.skillswap.modules.course;

import com.fptu.exe.skillswap.modules.course.domain.Course;
import com.fptu.exe.skillswap.modules.course.domain.CourseEnrollment;
import com.fptu.exe.skillswap.modules.course.domain.EnrollmentStatus;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseRepository;
import com.fptu.exe.skillswap.modules.course.service.CourseEnrollmentService;
import com.fptu.exe.skillswap.modules.course.service.CourseSettlementService;
import com.fptu.exe.skillswap.modules.payment.port.CoursePaymentPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseEnrollmentServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseEnrollmentRepository enrollmentRepository;

    @Mock
    private CoursePaymentPort coursePaymentPort;

    @Mock
    private CourseSettlementService settlementService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CourseEnrollmentService enrollmentService;

    private UUID studentUserId;
    private UUID courseId;
    private Course course;

    @BeforeEach
    void setUp() {
        studentUserId = UUID.randomUUID();
        courseId = UUID.randomUUID();

        course = Course.builder()
                .id(courseId)
                .title("Test Course")
                .priceScoin(100)
                .build();
        lenient().when(coursePaymentPort.quoteEnrollment(100)).thenReturn(
                new CoursePaymentPort.CoursePaymentQuote(100, 0, 100, 0, 100));
    }

    @Test
    void testEnrollStudentPublishesActivationWithoutCreatingChatMembership() {
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(enrollmentRepository.existsByCourseIdAndStudentUserId(courseId, studentUserId)).thenReturn(false);
        when(enrollmentRepository.save(any(CourseEnrollment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CourseEnrollment result = enrollmentService.enrollStudent(studentUserId, courseId);

        assertNotNull(result);
        assertEquals(EnrollmentStatus.ACTIVE, result.getStatus());
        verify(eventPublisher).publishEvent(any(com.fptu.exe.skillswap.modules.course.event.CourseEnrollmentActivatedEvent.class));
        verify(settlementService).generateSettlements(any(CourseEnrollment.class));
    }

    @Test
    void enrollStudentUsesConfiguredCourseFeePolicy() {
        course.setPriceScoin(100_000);
        when(coursePaymentPort.quoteEnrollment(100_000)).thenReturn(
                new CoursePaymentPort.CoursePaymentQuote(100_000, 10_000, 110_000, 5_000, 95_000));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(enrollmentRepository.existsByCourseIdAndStudentUserId(courseId, studentUserId)).thenReturn(false);
        when(enrollmentRepository.save(any(CourseEnrollment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CourseEnrollment result = enrollmentService.enrollStudent(studentUserId, courseId);

        assertEquals(10_000, result.getBuyerFeeScoin());
        assertEquals(110_000, result.getPaidAmountScoin());
        assertEquals(5_000, result.getMentorCommissionScoin());
        assertEquals(95_000, result.getMentorPayoutScoin());
    }
}
