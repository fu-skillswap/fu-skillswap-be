package com.fptu.exe.skillswap.modules.course.repository;

import com.fptu.exe.skillswap.modules.course.domain.CourseEnrollmentSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CourseEnrollmentSettlementRepository extends JpaRepository<CourseEnrollmentSettlement, UUID> {
    List<CourseEnrollmentSettlement> findByCourseSessionId(UUID courseSessionId);
    
    List<CourseEnrollmentSettlement> findByStatus(String status);
}
