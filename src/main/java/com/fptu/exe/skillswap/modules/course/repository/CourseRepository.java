package com.fptu.exe.skillswap.modules.course.repository;

import com.fptu.exe.skillswap.modules.course.domain.Course;
import com.fptu.exe.skillswap.modules.course.domain.CourseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseRepository extends JpaRepository<Course, UUID> {
    Page<Course> findByMentorUserId(UUID mentorUserId, Pageable pageable);
    
    Optional<Course> findByIdAndMentorUserId(UUID id, UUID mentorUserId);

    @Query("select c.mentorUserId from Course c where c.id = :courseId")
    Optional<UUID> findMentorUserIdByCourseId(@Param("courseId") UUID courseId);
    
    Page<Course> findByStatus(CourseStatus status, Pageable pageable);
    
    @Modifying
    @Query("UPDATE Course c SET c.enrolledCount = c.enrolledCount + 1 WHERE c.id = :id")
    int incrementEnrolledCount(@Param("id") UUID id);
}
