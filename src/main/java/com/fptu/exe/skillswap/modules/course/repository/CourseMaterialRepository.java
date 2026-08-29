package com.fptu.exe.skillswap.modules.course.repository;

import com.fptu.exe.skillswap.modules.course.domain.CourseMaterial;
import com.fptu.exe.skillswap.modules.course.domain.MaterialStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseMaterialRepository extends JpaRepository<CourseMaterial, UUID> {

    List<CourseMaterial> findByChapterIdAndDeletedAtIsNullOrderBySortOrderAsc(UUID chapterId);

    @Query("""
            select material from CourseMaterial material
            join fetch material.chapter chapter
            join fetch chapter.course
            where chapter.course.id = :courseId and material.deletedAt is null
            order by chapter.sortOrder asc, material.sortOrder asc
            """)
    List<CourseMaterial> findActiveByCourseIdOrderByCurriculum(UUID courseId);

    @Query("""
            select material from CourseMaterial material
            join fetch material.chapter chapter
            join fetch chapter.course
            where material.id = :materialId and material.deletedAt is null
            """)
    Optional<CourseMaterial> findActiveWithCurriculumById(UUID materialId);

    Optional<CourseMaterial> findByBunnyVideoId(String bunnyVideoId);

    long countByChapterCourseIdAndDeletedAtIsNullAndIsPublishedTrue(UUID courseId);

    long countByChapterCourseIdAndDeletedAtIsNullAndIsPublishedTrueAndDurationSecondsIsNotNull(UUID courseId);

    List<CourseMaterial> findByChapterIdInAndDeletedAtIsNullOrderBySortOrderAsc(Collection<UUID> chapterIds);

    List<CourseMaterial> findByStatusAndUploadExpiresAtBefore(MaterialStatus status, java.time.Instant cutoff);
}
