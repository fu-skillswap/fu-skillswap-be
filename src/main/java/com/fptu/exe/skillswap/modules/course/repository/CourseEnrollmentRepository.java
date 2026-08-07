package com.fptu.exe.skillswap.modules.course.repository;

import com.fptu.exe.skillswap.modules.course.domain.CourseEnrollment;
import com.fptu.exe.skillswap.modules.course.domain.EnrollmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, UUID> {
    Optional<CourseEnrollment> findByCourseIdAndStudentUserId(UUID courseId, UUID studentUserId);
    
    boolean existsByCourseIdAndStudentUserId(UUID courseId, UUID studentUserId);
    
    List<CourseEnrollment> findByCourseId(UUID courseId);
    
    boolean existsByCourseIdAndStudentUserIdAndStatusIn(UUID courseId, UUID studentUserId, List<EnrollmentStatus> statuses);
    
    List<CourseEnrollment> findByStatusAndSeatReservedUntilBefore(EnrollmentStatus status, Instant now);
    
    Optional<CourseEnrollment> findByPaymentOrderId(UUID paymentOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select enrollment from CourseEnrollment enrollment where enrollment.id = :id")
    Optional<CourseEnrollment> findByIdForUpdate(@Param("id") UUID id);
}
