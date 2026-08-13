package com.fptu.exe.skillswap.modules.course.repository;

import com.fptu.exe.skillswap.modules.course.domain.CourseLecture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CourseLectureRepository extends JpaRepository<CourseLecture, UUID> {
    List<CourseLecture> findByChapterIdOrderBySortOrderAsc(UUID chapterId);
    List<CourseLecture> findByChapterIdAndIsPublishedTrueOrderBySortOrderAsc(UUID chapterId);
    
    @Query("select count(l) from CourseLecture l where l.chapter.course.id = :courseId")
    int countByCourseId(@Param("courseId") UUID courseId);

    @Query("select coalesce(sum(l.durationSeconds), 0) from CourseLecture l where l.chapter.course.id = :courseId")
    int sumDurationSecondsByCourseId(@Param("courseId") UUID courseId);
}
