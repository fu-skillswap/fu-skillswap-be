package com.fptu.exe.skillswap.modules.course.repository;

import com.fptu.exe.skillswap.modules.course.domain.CourseMaterialProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseMaterialProgressRepository extends JpaRepository<CourseMaterialProgress, UUID> {

    Optional<CourseMaterialProgress> findByStudentUserIdAndMaterialId(UUID studentUserId, UUID materialId);

    @Query("""
            select progress from CourseMaterialProgress progress
            where progress.studentUserId = :studentUserId
              and progress.material.chapter.course.id = :courseId
            """)
    List<CourseMaterialProgress> findByStudentUserIdAndCourseId(UUID studentUserId, UUID courseId);

    @Query("""
            select count(progress) from CourseMaterialProgress progress
            where progress.studentUserId = :studentUserId
              and progress.material.chapter.course.id = :courseId
              and progress.isCompleted = true
              and progress.material.deletedAt is null
              and progress.material.isPublished = true
              and progress.material.chapter.isPublished = true
            """)
    int countCompletedPublishedByStudentUserIdAndCourseId(UUID studentUserId, UUID courseId);
}
