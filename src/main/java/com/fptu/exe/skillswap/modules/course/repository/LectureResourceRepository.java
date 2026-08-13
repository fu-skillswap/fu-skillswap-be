package com.fptu.exe.skillswap.modules.course.repository;

import com.fptu.exe.skillswap.modules.course.domain.LectureResource;
import com.fptu.exe.skillswap.modules.course.domain.MaterialStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LectureResourceRepository extends JpaRepository<LectureResource, UUID> {
    List<LectureResource> findByLectureIdAndStatus(UUID lectureId, MaterialStatus status);
    Optional<LectureResource> findByBunnyVideoId(String bunnyVideoId);
}
