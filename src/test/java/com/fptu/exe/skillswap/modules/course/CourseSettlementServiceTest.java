package com.fptu.exe.skillswap.modules.course;

import com.fptu.exe.skillswap.modules.course.domain.Course;
import com.fptu.exe.skillswap.modules.course.domain.CourseEnrollment;
import com.fptu.exe.skillswap.modules.course.domain.CourseEnrollmentSettlement;
import com.fptu.exe.skillswap.modules.course.domain.CourseSettlementStatus;
import com.fptu.exe.skillswap.modules.course.domain.EnrollmentStatus;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentSettlementRepository;
import com.fptu.exe.skillswap.modules.course.service.CourseSettlementService;
import com.fptu.exe.skillswap.modules.payment.port.CoursePaymentPort;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CourseSettlementServiceTest {

    @Mock
    private CourseEnrollmentSettlementRepository settlementRepository;

    @Mock
    private CourseEnrollmentRepository enrollmentRepository;

    @Mock
    private CoursePaymentPort coursePaymentPort;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Mock
    private TimeProvider timeProvider;

    @InjectMocks
    private CourseSettlementService courseSettlementService;

    private UUID enrollmentId;
    private UUID courseId;
    private UUID studentUserId;
    private Course course;
    private CourseEnrollment enrollment;
    private CourseEnrollmentSettlement allocation;

    @BeforeEach
    void setUp() {
        lenient().when(timeProvider.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        enrollmentId = UUID.randomUUID();
        courseId = UUID.randomUUID();
        studentUserId = UUID.randomUUID();

        course = Course.builder()
                .id(courseId)
                .build();

        enrollment = CourseEnrollment.builder()
                .id(enrollmentId)
                .course(course)
                .studentUserId(studentUserId)
                .status(EnrollmentStatus.ACTIVE)
                .build();

        allocation = CourseEnrollmentSettlement.builder()
                .id(UUID.randomUUID())
                .enrollment(enrollment)
                .studentRefundableScoin(100)
                .status(CourseSettlementStatus.HELD)
                .build();
    }

    @Test
    void testRefundUnreleasedAllocationsRevokesGroupChatAccess() {
        when(enrollmentRepository.findByIdForUpdate(enrollmentId)).thenReturn(Optional.of(enrollment));
        when(settlementRepository.findByEnrollmentIdForUpdate(enrollmentId)).thenReturn(Optional.of(allocation));
        int refunded = courseSettlementService.refundUnreleasedAllocations(enrollmentId, "STUDENT_REQUEST");

        assertEquals(100, refunded);
        assertEquals(EnrollmentStatus.REFUNDED, enrollment.getStatus());
        assertEquals(CourseSettlementStatus.REFUNDED, allocation.getStatus());
        verify(eventPublisher).publishEvent(any(com.fptu.exe.skillswap.modules.course.event.CourseEnrollmentEndedEvent.class));
    }
}
