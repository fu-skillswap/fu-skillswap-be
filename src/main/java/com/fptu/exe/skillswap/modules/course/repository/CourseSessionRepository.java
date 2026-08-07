package com.fptu.exe.skillswap.modules.course.repository;

import com.fptu.exe.skillswap.modules.course.domain.CourseSession;
import com.fptu.exe.skillswap.modules.course.domain.CourseSessionStatus;
import com.fptu.exe.skillswap.modules.course.domain.CourseSettlementStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseSessionRepository extends JpaRepository<CourseSession, UUID> {
    List<CourseSession> findByCourseIdOrderBySessionNumberAsc(UUID courseId);
    
    List<CourseSession> findByCourseIdOrderByScheduledStartAtAsc(UUID courseId);
    
    Optional<CourseSession> findByCourseIdAndSessionNumber(UUID courseId, int sessionNumber);

    Optional<CourseSession> findByIdAndCourseId(UUID id, UUID courseId);

    @Query("""
            select session.id from CourseSession session
            where session.status = :sessionStatus
              and session.completedAt is not null
              and exists (
                  select 1 from CourseEnrollmentSettlement allocation
                  where allocation.courseSession = session
                    and allocation.status = :allocationStatus
              )
            order by session.completedAt asc, session.id asc
            """)
    List<UUID> findCompletedSessionIdsWithAllocationStatus(
            @Param("sessionStatus") CourseSessionStatus sessionStatus,
            @Param("allocationStatus") CourseSettlementStatus allocationStatus,
            Pageable pageable);
}
