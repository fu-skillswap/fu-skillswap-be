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
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseEnrollmentSettlementRepository extends JpaRepository<CourseEnrollmentSettlement, UUID> {
    Optional<CourseEnrollmentSettlement> findByEnrollmentId(UUID enrollmentId);
    
    List<CourseEnrollmentSettlement> findByStatus(CourseSettlementStatus status);

    List<CourseEnrollmentSettlement> findTop100ByStatusAndEligibleAtBeforeOrderByEligibleAtAsc(
            CourseSettlementStatus status, java.time.Instant eligibleAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select settlement from CourseEnrollmentSettlement settlement where settlement.id = :id")
    Optional<CourseEnrollmentSettlement> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select settlement from CourseEnrollmentSettlement settlement
            where settlement.enrollment.id = :enrollmentId
            """)
    Optional<CourseEnrollmentSettlement> findByEnrollmentIdForUpdate(@Param("enrollmentId") UUID enrollmentId);
}
