package com.fptu.exe.skillswap.modules.course;

import com.fptu.exe.skillswap.modules.course.domain.Course;
import com.fptu.exe.skillswap.modules.course.domain.CourseEnrollment;
import com.fptu.exe.skillswap.modules.course.domain.CourseEnrollmentSettlement;
import com.fptu.exe.skillswap.modules.course.domain.CourseSession;
import com.fptu.exe.skillswap.modules.course.domain.CourseSettlementStatus;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentSettlementRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseSessionRepository;
import com.fptu.exe.skillswap.modules.course.service.CourseSettlementService;
import com.fptu.exe.skillswap.modules.payment.service.CreditLedgerService;
import com.fptu.exe.skillswap.modules.payment.service.SettlementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseSettlementServiceTest {

    @Mock
    private CourseEnrollmentSettlementRepository settlementRepository;
    @Mock
    private CourseSessionRepository sessionRepository;
    @Mock
    private CourseEnrollmentRepository enrollmentRepository;
    @Mock
    private CreditLedgerService creditLedgerService;
    @Mock
    private SettlementService paymentSettlementService;

    @InjectMocks
    private CourseSettlementService settlementService;

    @Test
    void generatesImmutableAllocationsWhosePerSessionRevenueBalances() {
        UUID courseId = UUID.randomUUID();
        Course course = Course.builder().id(courseId).build();
        CourseEnrollment enrollment = enrollment(course, 100, 9, 6, 94);
        List<CourseSession> sessions = sessions(3);

        when(enrollmentRepository.findByIdForUpdate(enrollment.getId())).thenReturn(Optional.of(enrollment));
        when(sessionRepository.findByCourseIdOrderByScheduledStartAtAsc(courseId)).thenReturn(sessions);
        when(settlementRepository.findByEnrollmentId(enrollment.getId())).thenReturn(List.of());

        settlementService.generateSettlements(enrollment);

        ArgumentCaptor<CourseEnrollmentSettlement> captured = ArgumentCaptor.forClass(CourseEnrollmentSettlement.class);
        verify(settlementRepository, times(3)).save(captured.capture());
        List<CourseEnrollmentSettlement> allocations = captured.getAllValues();
        assertEquals(100, allocations.stream().mapToInt(CourseEnrollmentSettlement::getBasePriceScoin).sum());
        assertEquals(9, allocations.stream().mapToInt(CourseEnrollmentSettlement::getBuyerFeeScoin).sum());
        assertEquals(6, allocations.stream().mapToInt(CourseEnrollmentSettlement::getMentorCommissionScoin).sum());
        assertEquals(94, allocations.stream().mapToInt(CourseEnrollmentSettlement::getMentorPayoutScoin).sum());
        assertEquals(15, allocations.stream().mapToInt(CourseEnrollmentSettlement::getPlatformRevenueScoin).sum());
        assertEquals(100, allocations.stream().mapToInt(CourseEnrollmentSettlement::getStudentRefundableScoin).sum());
        allocations.forEach(allocation -> {
            assertEquals(allocation.getBuyerFeeScoin() + allocation.getMentorCommissionScoin(),
                    allocation.getPlatformRevenueScoin());
            assertEquals(CourseSettlementStatus.HELD, allocation.getStatus());
        });
    }

    @Test
    void doesNotRewriteACompleteExistingAllocationSet() {
        UUID courseId = UUID.randomUUID();
        Course course = Course.builder().id(courseId).build();
        CourseEnrollment enrollment = enrollment(course, 100, 9, 6, 94);
        List<CourseSession> sessions = sessions(2);
        List<CourseEnrollmentSettlement> existing = sessions.stream()
                .map(session -> CourseEnrollmentSettlement.builder()
                        .enrollment(enrollment).courseSession(session).status(CourseSettlementStatus.HELD).build())
                .toList();

        when(enrollmentRepository.findByIdForUpdate(enrollment.getId())).thenReturn(Optional.of(enrollment));
        when(sessionRepository.findByCourseIdOrderByScheduledStartAtAsc(courseId)).thenReturn(sessions);
        when(settlementRepository.findByEnrollmentId(enrollment.getId())).thenReturn(existing);

        settlementService.generateSettlements(enrollment);

        verify(settlementRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refusesToFillInOnlyPartOfAnExistingAllocationSet() {
        UUID courseId = UUID.randomUUID();
        Course course = Course.builder().id(courseId).build();
        CourseEnrollment enrollment = enrollment(course, 100, 9, 6, 94);
        List<CourseSession> sessions = sessions(2);
        CourseEnrollmentSettlement existing = CourseEnrollmentSettlement.builder()
                .enrollment(enrollment).courseSession(sessions.getFirst()).status(CourseSettlementStatus.HELD).build();

        when(enrollmentRepository.findByIdForUpdate(enrollment.getId())).thenReturn(Optional.of(enrollment));
        when(sessionRepository.findByCourseIdOrderByScheduledStartAtAsc(courseId)).thenReturn(sessions);
        when(settlementRepository.findByEnrollmentId(enrollment.getId())).thenReturn(List.of(existing));

        assertThrows(IllegalStateException.class, () -> settlementService.generateSettlements(enrollment));
        verify(settlementRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private CourseEnrollment enrollment(Course course, int basePrice, int buyerFee, int commission, int payout) {
        return CourseEnrollment.builder()
                .id(UUID.randomUUID())
                .course(course)
                .basePriceScoin(basePrice)
                .buyerFeeScoin(buyerFee)
                .mentorCommissionScoin(commission)
                .mentorPayoutScoin(payout)
                .build();
    }

    private List<CourseSession> sessions(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(number -> CourseSession.builder().id(UUID.randomUUID()).sessionNumber(number).build())
                .toList();
    }
}
