package com.fptu.exe.skillswap.modules.course.repository;

import com.fptu.exe.skillswap.modules.course.domain.CourseChapter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CourseChapterRepository extends JpaRepository<CourseChapter, UUID> {
    List<CourseChapter> findByCourseIdOrderBySortOrderAsc(UUID courseId);
    List<CourseChapter> findByCourseIdAndIsPublishedTrueOrderBySortOrderAsc(UUID courseId);
    int countByCourseId(UUID courseId);

    @Query("select coalesce(max(c.sortOrder), 0) from CourseChapter c where c.course.id = :courseId")
    int findMaxSortOrderByCourseId(UUID courseId);
}
