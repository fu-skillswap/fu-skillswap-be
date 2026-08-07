package com.fptu.exe.skillswap.modules.course;

import com.fptu.exe.skillswap.infrastructure.bunny.client.BunnyVideoClient;
import com.fptu.exe.skillswap.infrastructure.bunny.config.BunnyStreamProperties;
import com.fptu.exe.skillswap.modules.course.domain.Course;
import com.fptu.exe.skillswap.modules.course.domain.CourseMaterial;
import com.fptu.exe.skillswap.modules.course.domain.MaterialStatus;
import com.fptu.exe.skillswap.modules.course.dto.request.CreateVideoMaterialRequest;
import com.fptu.exe.skillswap.modules.course.repository.BunnyWebhookEventRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseMaterialRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseOutboxEventRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseSessionRepository;
import com.fptu.exe.skillswap.modules.course.service.CourseVaultServiceImpl;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.shared.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseVaultServiceImplTest {

    @Mock private CourseRepository courseRepository;
    @Mock private CourseSessionRepository sessionRepository;
    @Mock private CourseEnrollmentRepository enrollmentRepository;
    @Mock private CourseMaterialRepository materialRepository;
    @Mock private BunnyWebhookEventRepository webhookEventRepository;
    @Mock private BunnyVideoClient bunnyVideoClient;
    @Mock private BunnyStreamProperties bunnyProperties;
    @Mock private CourseOutboxEventRepository outboxEventRepository;
    @Mock private TransactionTemplate transactionTemplate;

    private CourseVaultServiceImpl vaultService;
    private UUID mentorUserId;
    private UUID courseId;
    private Course course;

    @BeforeEach
    void setUp() {
        vaultService = new CourseVaultServiceImpl(courseRepository, sessionRepository, enrollmentRepository,
                materialRepository, webhookEventRepository, bunnyVideoClient, bunnyProperties, outboxEventRepository, transactionTemplate);
        org.mockito.Mockito.lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(org.mockito.Mockito.mock(TransactionStatus.class)));
        mentorUserId = UUID.randomUUID();
        courseId = UUID.randomUUID();
        course = Course.builder()
                .id(courseId)
                .mentorProfile(MentorProfile.builder().userId(mentorUserId).build())
                .build();
    }

    @Test
    void createVideoUploadRejectsSessionFromAnotherCourseBeforeCallingProvider() {
        UUID foreignSessionId = UUID.randomUUID();
        CreateVideoMaterialRequest request = new CreateVideoMaterialRequest();
        request.setTitle("Lesson 1");
        request.setCourseSessionId(foreignSessionId);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(sessionRepository.findByIdAndCourseId(foreignSessionId, courseId)).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class,
                () -> vaultService.createVideoUpload(mentorUserId, courseId, request));

        verify(bunnyVideoClient, never()).createVideo(anyString());
        verify(materialRepository, never()).save(any(CourseMaterial.class));
    }

    @Test
    void getCourseMaterialsExcludesDeletedRows() {
        UUID studentUserId = UUID.randomUUID();
        CourseMaterial active = CourseMaterial.builder()
                .id(UUID.randomUUID())
                .course(course)
                .title("Active material")
                .status(MaterialStatus.READY)
                .build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByCourseIdAndStudentUserId(courseId, studentUserId)).thenReturn(Optional.empty());
        when(materialRepository.findByCourseIdAndStatusNotAndDeletedAtIsNullOrderByUploadedAtAsc(
                courseId, MaterialStatus.DELETED)).thenReturn(List.of(active));

        vaultService.getCourseMaterials(studentUserId, courseId);

        verify(materialRepository).findByCourseIdAndStatusNotAndDeletedAtIsNullOrderByUploadedAtAsc(
                courseId, MaterialStatus.DELETED);
    }

    @Test
    void deleteMaterialIsIdempotentForAlreadyRequestedDeletion() {
        UUID materialId = UUID.randomUUID();
        CourseMaterial material = CourseMaterial.builder()
                .id(materialId)
                .course(course)
                .status(MaterialStatus.DELETING)
                .deleteRequestedAt(Instant.now())
                .build();

        when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));

        vaultService.deleteMaterial(mentorUserId, courseId, materialId);

        verify(materialRepository, never()).save(any(CourseMaterial.class));
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void deleteMaterialRequestsProviderDeletionOnceForActiveMaterial() {
        UUID materialId = UUID.randomUUID();
        CourseMaterial material = CourseMaterial.builder()
                .id(materialId)
                .course(course)
                .status(MaterialStatus.READY)
                .build();

        when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));

        vaultService.deleteMaterial(mentorUserId, courseId, materialId);

        verify(materialRepository).save(material);
        verify(outboxEventRepository).save(any());
        assertEquals(MaterialStatus.DELETING, material.getStatus());
        assertNotNull(material.getDeleteRequestedAt());
    }
}
