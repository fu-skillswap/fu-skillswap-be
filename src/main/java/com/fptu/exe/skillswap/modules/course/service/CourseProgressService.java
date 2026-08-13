package com.fptu.exe.skillswap.modules.course.service;

import com.fptu.exe.skillswap.modules.course.domain.Course;
import com.fptu.exe.skillswap.modules.course.domain.CourseLecture;
import com.fptu.exe.skillswap.modules.course.domain.CourseProgress;
import com.fptu.exe.skillswap.modules.course.domain.LectureProgress;
import com.fptu.exe.skillswap.modules.course.repository.CourseLectureRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseProgressRepository;

import com.fptu.exe.skillswap.modules.course.repository.CourseRepository;
import com.fptu.exe.skillswap.modules.course.repository.LectureProgressRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseProgressService {

    private final LectureProgressRepository lectureProgressRepository;
    private final CourseProgressRepository courseProgressRepository;
    private final CourseLectureRepository lectureRepository;
    private final CourseRepository courseRepository;

    private static final int COMPLETION_THRESHOLD_PERCENT = 90;



    @Transactional
    public LectureProgress updateLectureProgress(UUID studentUserId, UUID lectureId, int watchedSeconds) {
        CourseLecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Lecture not found"));

        LectureProgress progress = lectureProgressRepository
                .findByStudentUserIdAndLectureId(studentUserId, lectureId)
                .orElseGet(() -> {
                    LectureProgress p = LectureProgress.builder()
                            .studentUserId(studentUserId)
                            .lecture(lecture)
                            .watchedSeconds(0)
                            .completionPercentage(0)
                            .isCompleted(false)
                            .build();
                    p.setCreatedAt(Instant.now());
                    return p;
                });

        progress.setWatchedSeconds(watchedSeconds);
        progress.setLastAccessedAt(Instant.now());

        if (lecture.getDurationSeconds() > 0) {
            int percentage = Math.min(100, (int) Math.round(((double) watchedSeconds / lecture.getDurationSeconds()) * 100));
            progress.setCompletionPercentage(percentage);

            if (percentage >= COMPLETION_THRESHOLD_PERCENT && !progress.isCompleted()) {
                progress.setCompleted(true);
                progress.setCompletedAt(Instant.now());
            }
        }

        progress = lectureProgressRepository.save(progress);

        // Update overall course progress
        updateCourseProgress(studentUserId, lecture.getChapter().getCourse().getId(), lecture);

        return progress;
    }

    @Transactional
    public CourseProgress updateCourseProgress(UUID studentUserId, UUID courseId, CourseLecture lastStudied) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Course not found"));

        int totalLectures = lectureRepository.countByCourseId(courseId);
        int completedCount = lectureProgressRepository.countByStudentUserIdAndLectureChapterCourseIdAndIsCompletedTrue(studentUserId, courseId);

        CourseProgress courseProgress = courseProgressRepository
                .findByStudentUserIdAndCourseId(studentUserId, courseId)
                .orElseGet(() -> CourseProgress.builder()
                        .studentUserId(studentUserId)
                        .course(course)
                        .build());

        courseProgress.setTotalLectures(totalLectures);
        courseProgress.setCompletedLectures(completedCount);
        courseProgress.setLastStudiedLecture(lastStudied);

        int overall = totalLectures > 0 ? (int) Math.round(((double) completedCount / totalLectures) * 100) : 0;
        courseProgress.setOverallPercentage(overall);

        if (overall >= 100 && courseProgress.getCompletedAt() == null) {
            courseProgress.setCompletedAt(Instant.now());
        }

        return courseProgressRepository.save(courseProgress);
    }
}
