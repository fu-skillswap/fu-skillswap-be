package com.fptu.exe.skillswap.modules.course.repository;

import com.fptu.exe.skillswap.modules.course.domain.LectureProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LectureProgressRepository extends JpaRepository<LectureProgress, UUID> {
    Optional<LectureProgress> findByStudentUserIdAndLectureId(UUID studentUserId, UUID lectureId);
    List<LectureProgress> findByStudentUserIdAndLectureChapterCourseId(UUID studentUserId, UUID courseId);
    int countByStudentUserIdAndLectureChapterCourseIdAndIsCompletedTrue(UUID studentUserId, UUID courseId);
}
