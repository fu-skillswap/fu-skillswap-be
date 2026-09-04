package com.fptu.exe.skillswap.modules.course.repository;

import com.fptu.exe.skillswap.modules.course.domain.CourseAnnouncement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CourseAnnouncementRepository extends JpaRepository<CourseAnnouncement, UUID> {

    @EntityGraph(attributePaths = "course")
    java.util.Optional<CourseAnnouncement> findWithCourseById(UUID announcementId);

    Page<CourseAnnouncement> findByCourseIdAndDeletedAtIsNullOrderByPublishedAtDescIdDesc(
            UUID courseId,
            Pageable pageable
    );
}
