package com.fptu.exe.skillswap.modules.course.service;

import com.fptu.exe.skillswap.modules.course.domain.Course;
import com.fptu.exe.skillswap.modules.course.domain.CourseMaterial;
import com.fptu.exe.skillswap.modules.course.domain.CourseMaterialProgress;
import com.fptu.exe.skillswap.modules.course.domain.CourseProgress;
import com.fptu.exe.skillswap.modules.course.repository.CourseMaterialProgressRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseMaterialRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseProgressRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Progress is tracked against a learning material, never an implementation-only lecture. */
@Service
@RequiredArgsConstructor
public class CourseProgressService {
    private static final int COMPLETION_THRESHOLD_PERCENT = 90;
    private final CourseMaterialProgressRepository materialProgressRepository;
    private final CourseProgressRepository courseProgressRepository;
    private final CourseMaterialRepository materialRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final TimeProvider timeProvider;

    @Transactional
    public CourseMaterialProgress updateMaterialProgress(UUID studentUserId, UUID courseId, UUID materialId, int watchedSeconds) {
        CourseMaterial material = materialRepository.findActiveWithCurriculumById(materialId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Course material not found"));
        if (!material.getChapter().getCourse().getId().equals(courseId)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Material does not belong to specified course");
        }
        boolean isMentor = material.getChapter().getCourse().getMentorUserId().equals(studentUserId);
        if (!isMentor && enrollmentRepository.findByCourseIdAndStudentUserId(courseId, studentUserId)
                .filter(enrollment -> enrollment.getStatus() == com.fptu.exe.skillswap.modules.course.domain.EnrollmentStatus.ACTIVE
                        || enrollment.getStatus() == com.fptu.exe.skillswap.modules.course.domain.EnrollmentStatus.COMPLETED)
                .isEmpty()) {
            throw new BaseException(ErrorCode.COURSE_ACCESS_DENIED)
                    .withLogContext("courseId", courseId);
        }
        if (material.getDurationSeconds() == null || material.getDurationSeconds() <= 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Progress is available only after video duration is known");
        }
        CourseMaterialProgress progress = materialProgressRepository.findByStudentUserIdAndMaterialId(studentUserId, materialId)
                .orElseGet(() -> CourseMaterialProgress.builder().studentUserId(studentUserId).material(material).build());
        int safeWatchedSeconds = Math.max(0, watchedSeconds);
        progress.setWatchedSeconds(safeWatchedSeconds);
        progress.setLastAccessedAt(timeProvider.instant());
        int percentage = Math.min(100, (int) Math.round((double) safeWatchedSeconds * 100 / material.getDurationSeconds()));
        progress.setCompletionPercentage(percentage);
        if (percentage >= COMPLETION_THRESHOLD_PERCENT && !progress.isCompleted()) {
            progress.setCompleted(true);
            progress.setCompletedAt(timeProvider.instant());
        }
        progress = materialProgressRepository.save(progress);
        updateCourseProgress(studentUserId, courseId, material);
        return progress;
    }

    @Transactional
    public CourseProgress updateCourseProgress(UUID studentUserId, UUID courseId, CourseMaterial lastStudied) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Course not found"));
        int total = Math.toIntExact(materialRepository.countByChapterCourseIdAndDeletedAtIsNullAndIsPublishedTrue(courseId));
        int completed = materialProgressRepository.countCompletedPublishedByStudentUserIdAndCourseId(studentUserId, courseId);
        CourseProgress progress = courseProgressRepository.findByStudentUserIdAndCourseId(studentUserId, courseId)
                .orElseGet(() -> CourseProgress.builder().studentUserId(studentUserId).course(course).build());
        progress.setTotalMaterials(total);
        progress.setCompletedMaterials(completed);
        progress.setLastStudiedMaterial(lastStudied);
        int overall = total == 0 ? 0 : (int) Math.round((double) completed * 100 / total);
        progress.setOverallPercentage(overall);
        if (overall == 100 && progress.getCompletedAt() == null) {
            progress.setCompletedAt(timeProvider.instant());
        }
        return courseProgressRepository.save(progress);
    }
}
