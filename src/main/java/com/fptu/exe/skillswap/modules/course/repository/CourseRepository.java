package com.fptu.exe.skillswap.modules.course.repository;

import com.fptu.exe.skillswap.modules.course.domain.Course;
import com.fptu.exe.skillswap.modules.course.domain.CourseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseRepository extends JpaRepository<Course, UUID> {
    Page<Course> findByMentorProfileUserId(UUID mentorUserId, Pageable pageable);
    
    Optional<Course> findByIdAndMentorProfileUserId(UUID id, UUID mentorUserId);
    
    Page<Course> findByStatus(CourseStatus status, Pageable pageable);
    
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Course c SET c.confirmedCount = c.confirmedCount + 1 WHERE c.id = :id AND c.confirmedCount < :maxStudents")
    int incrementConfirmedCount(@org.springframework.data.repository.query.Param("id") UUID id, @org.springframework.data.repository.query.Param("maxStudents") int maxStudents);
}
