package com.fptu.exe.skillswap.modules.course.repository;

import com.fptu.exe.skillswap.modules.course.domain.CourseSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseSessionRepository extends JpaRepository<CourseSession, UUID> {
    List<CourseSession> findByCourseIdOrderBySessionNumberAsc(UUID courseId);
    
    List<CourseSession> findByCourseIdOrderByScheduledStartAtAsc(UUID courseId);
    
    Optional<CourseSession> findByCourseIdAndSessionNumber(UUID courseId, int sessionNumber);
}
