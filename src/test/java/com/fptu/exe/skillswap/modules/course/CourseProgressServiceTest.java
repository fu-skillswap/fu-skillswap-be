package com.fptu.exe.skillswap.modules.course;

import com.fptu.exe.skillswap.modules.course.domain.Course;
import com.fptu.exe.skillswap.modules.course.domain.CourseChapter;
import com.fptu.exe.skillswap.modules.course.domain.CourseEnrollment;
import com.fptu.exe.skillswap.modules.course.domain.CourseMaterial;
import com.fptu.exe.skillswap.modules.course.domain.CourseMaterialProgress;
import com.fptu.exe.skillswap.modules.course.domain.CourseMaterialType;
import com.fptu.exe.skillswap.modules.course.domain.CourseProgress;
import com.fptu.exe.skillswap.modules.course.domain.EnrollmentStatus;
import com.fptu.exe.skillswap.modules.course.domain.MaterialStatus;
import com.fptu.exe.skillswap.modules.course.domain.StorageProviderType;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseMaterialProgressRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseMaterialRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseProgressRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseRepository;
import com.fptu.exe.skillswap.modules.course.service.CourseProgressService;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseProgressServiceTest {
    @Mock private CourseMaterialProgressRepository materialProgressRepository;
    @Mock private CourseProgressRepository courseProgressRepository;
    @Mock private CourseMaterialRepository materialRepository;
    @Mock private CourseEnrollmentRepository enrollmentRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private TimeProvider timeProvider;
    @InjectMocks private CourseProgressService service;

    @Test
    void readyR2VideoAllowsProgressUpdateAndUsesPersistedDuration() {
        UUID learnerId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        Course course = Course.builder().id(courseId).mentorUserId(UUID.randomUUID()).build();
        CourseChapter chapter = CourseChapter.builder().course(course).build();
        CourseMaterial material = CourseMaterial.builder()
                .id(materialId).chapter(chapter).materialType(CourseMaterialType.VIDEO)
                .storageProviderType(StorageProviderType.OBJECT_STORAGE).status(MaterialStatus.READY)
                .durationSeconds(420).uploadedBy(course.getMentorUserId())
                .uploadedAt(Instant.parse("2026-01-01T00:00:00Z")).build();
        CourseEnrollment enrollment = CourseEnrollment.builder().course(course).studentUserId(learnerId)
                .status(EnrollmentStatus.ACTIVE).build();

        when(materialRepository.findActiveWithCurriculumById(materialId)).thenReturn(Optional.of(material));
        when(enrollmentRepository.findByCourseIdAndStudentUserId(courseId, learnerId)).thenReturn(Optional.of(enrollment));
        when(materialProgressRepository.findByStudentUserIdAndMaterialId(learnerId, materialId)).thenReturn(Optional.empty());
        when(materialProgressRepository.save(any(CourseMaterialProgress.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(materialRepository.countByChapterCourseIdAndDeletedAtIsNullAndIsPublishedTrue(courseId)).thenReturn(1L);
        when(materialProgressRepository.countCompletedPublishedByStudentUserIdAndCourseId(learnerId, courseId)).thenReturn(0);
        when(courseProgressRepository.findByStudentUserIdAndCourseId(learnerId, courseId)).thenReturn(Optional.empty());
        when(courseProgressRepository.save(any(CourseProgress.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(timeProvider.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));

        CourseMaterialProgress progress = service.updateMaterialProgress(learnerId, courseId, materialId, 210);

        assertThat(progress.getWatchedSeconds()).isEqualTo(210);
        assertThat(progress.getCompletionPercentage()).isEqualTo(50);
        assertThat(progress.isCompleted()).isFalse();
    }
}
