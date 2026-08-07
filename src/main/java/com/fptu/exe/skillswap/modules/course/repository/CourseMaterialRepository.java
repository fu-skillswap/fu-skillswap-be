package com.fptu.exe.skillswap.modules.course.repository;

import com.fptu.exe.skillswap.modules.course.domain.CourseMaterial;
import com.fptu.exe.skillswap.modules.course.domain.MaterialStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseMaterialRepository extends JpaRepository<CourseMaterial, UUID> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"courseSession"})
    List<CourseMaterial> findByCourseIdAndStatusNotAndDeletedAtIsNullOrderByUploadedAtAsc(
            UUID courseId, MaterialStatus status);
    
    Optional<CourseMaterial> findByBunnyLibraryIdAndBunnyVideoId(String bunnyLibraryId, String bunnyVideoId);
    
    List<CourseMaterial> findByStatusAndUploadedAtBefore(MaterialStatus status, Instant timestamp);
}
