package com.fptu.exe.skillswap.modules.course;

import com.fptu.exe.skillswap.modules.chat.service.ConversationService;
import com.fptu.exe.skillswap.modules.course.domain.Course;
import com.fptu.exe.skillswap.modules.course.domain.CourseEnrollment;
import com.fptu.exe.skillswap.modules.course.domain.EnrollmentStatus;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseRepository;
import com.fptu.exe.skillswap.modules.course.service.CourseEnrollmentService;
import com.fptu.exe.skillswap.modules.course.service.CourseSettlementService;
import com.fptu.exe.skillswap.modules.payment.service.CreditLedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseEnrollmentServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseEnrollmentRepository enrollmentRepository;

    @Mock
    private CreditLedgerService creditLedgerService;

    @Mock
    private CourseSettlementService settlementService;

    @Mock
    private ObjectProvider<ConversationService> conversationServiceProvider;

    @Mock
    private ConversationService conversationService;

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
    }

    @Test
    void testEnrollStudentAutoJoinsGroupChat() {
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(enrollmentRepository.existsByCourseIdAndStudentUserId(courseId, studentUserId)).thenReturn(false);
        when(enrollmentRepository.save(any(CourseEnrollment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationServiceProvider.getIfAvailable()).thenReturn(conversationService);

        CourseEnrollment result = enrollmentService.enrollStudent(studentUserId, courseId);

        assertNotNull(result);
        assertEquals(EnrollmentStatus.ACTIVE, result.getStatus());
        verify(conversationService).addCourseStudentParticipant(eq(courseId), eq(studentUserId));
        verify(settlementService).generateSettlements(any(CourseEnrollment.class));
    }
}
