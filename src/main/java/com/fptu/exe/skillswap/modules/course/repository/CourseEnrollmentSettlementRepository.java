package com.fptu.exe.skillswap.modules.course.repository;

import com.fptu.exe.skillswap.modules.course.domain.CourseEnrollmentSettlement;
import com.fptu.exe.skillswap.modules.course.domain.CourseSettlementStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CourseEnrollmentSettlementRepository extends JpaRepository<CourseEnrollmentSettlement, UUID> {
    List<CourseEnrollmentSettlement> findByCourseSessionId(UUID courseSessionId);

    List<CourseEnrollmentSettlement> findByEnrollmentId(UUID enrollmentId);
    
    List<CourseEnrollmentSettlement> findByStatus(CourseSettlementStatus status);

    List<CourseEnrollmentSettlement> findTop100ByStatusAndEligibleAtBeforeOrderByEligibleAtAsc(
            CourseSettlementStatus status, java.time.Instant eligibleAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select settlement from CourseEnrollmentSettlement settlement where settlement.id = :id")
    java.util.Optional<CourseEnrollmentSettlement> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select settlement from CourseEnrollmentSettlement settlement
            join fetch settlement.enrollment enrollment
            where settlement.courseSession.id = :sessionId
            order by settlement.enrollment.id asc
            """)
    List<CourseEnrollmentSettlement> findByCourseSessionIdForUpdate(@Param("sessionId") UUID sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select settlement from CourseEnrollmentSettlement settlement
            join fetch settlement.courseSession
            where settlement.enrollment.id = :enrollmentId
            order by settlement.courseSession.scheduledStartAt asc, settlement.id asc
            """)
    List<CourseEnrollmentSettlement> findByEnrollmentIdForUpdate(@Param("enrollmentId") UUID enrollmentId);
}
