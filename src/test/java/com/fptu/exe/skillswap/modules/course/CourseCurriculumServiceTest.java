package com.fptu.exe.skillswap.modules.course;

import com.fptu.exe.skillswap.modules.course.domain.Course;
import com.fptu.exe.skillswap.modules.course.domain.CourseChapter;
import com.fptu.exe.skillswap.modules.course.repository.CourseChapterRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseMaterialProgressRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseMaterialRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseProgressRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseRepository;
import com.fptu.exe.skillswap.modules.course.service.CourseCurriculumService;
import com.fptu.exe.skillswap.modules.course.service.CourseVaultService;
import com.fptu.exe.skillswap.modules.mentor.port.MentorOwnershipQueryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseCurriculumServiceTest {
    @Mock private CourseRepository courseRepository;
    @Mock private CourseChapterRepository chapterRepository;
    @Mock private CourseMaterialRepository materialRepository;
    @Mock private CourseMaterialProgressRepository materialProgressRepository;
    @Mock private CourseProgressRepository courseProgressRepository;
    @Mock private CourseEnrollmentRepository enrollmentRepository;
    @Mock private CourseVaultService vaultService;
    @Mock private MentorOwnershipQueryPort mentorOwnershipQueryPort;
    @InjectMocks private CourseCurriculumService service;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID previousOwnerId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();

    @Test
    void currentOwnerCanManageCourseMaterial() {
        Course course = course();
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(mentorOwnershipQueryPort.isActiveOwner(ownerId, ownerId)).thenReturn(true);
        when(chapterRepository.countByCourseId(courseId)).thenReturn(0);
        when(chapterRepository.save(any(CourseChapter.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CourseChapter result = service.createChapter(ownerId, courseId,
                new com.fptu.exe.skillswap.modules.course.dto.request.CreateCourseChapterRequest("Chapter", null, true));

        assertThat(result.getCourse()).isEqualTo(course);
    }

    @Test
    void previousOwnerCannotManageAfterOwnershipChange() {
        Course course = course();
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(mentorOwnershipQueryPort.isActiveOwner(ownerId, previousOwnerId)).thenReturn(false);

        assertThatThrownBy(() -> service.createChapter(previousOwnerId, courseId,
                new com.fptu.exe.skillswap.modules.course.dto.request.CreateCourseChapterRequest("Chapter", null, true)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nonOwnerCannotModifyCurriculum() {
        Course course = course();
        UUID otherUserId = UUID.randomUUID();
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(mentorOwnershipQueryPort.isActiveOwner(ownerId, otherUserId)).thenReturn(false);

        assertThatThrownBy(() -> service.createChapter(otherUserId, courseId,
                new com.fptu.exe.skillswap.modules.course.dto.request.CreateCourseChapterRequest("Chapter", null, true)))
                .isInstanceOf(AccessDeniedException.class);
    }

    private Course course() {
        return Course.builder().id(courseId).mentorUserId(ownerId).title("Course").build();
    }
}
