package com.fptu.exe.skillswap.modules.course.service;

import com.fptu.exe.skillswap.infrastructure.config.CourseMaterialProperties;
import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.infrastructure.storage.VideoStoragePolicy;
import com.fptu.exe.skillswap.infrastructure.video.VideoPlaybackTokenService;
import com.fptu.exe.skillswap.modules.course.domain.Course;
import com.fptu.exe.skillswap.modules.course.domain.CourseChapter;
import com.fptu.exe.skillswap.modules.course.domain.CourseMaterial;
import com.fptu.exe.skillswap.modules.course.domain.CourseMaterialType;
import com.fptu.exe.skillswap.modules.course.domain.MaterialStatus;
import com.fptu.exe.skillswap.modules.course.domain.StorageProviderType;
import com.fptu.exe.skillswap.modules.course.repository.*;
import com.fptu.exe.skillswap.modules.course.port.CourseVideoProvider;
import com.fptu.exe.skillswap.modules.mentor.port.MentorOwnershipQueryPort;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseVaultErrorContractTest {

    @Mock private CourseRepository courseRepository;
    @Mock private CourseChapterRepository chapterRepository;
    @Mock private CourseEnrollmentRepository enrollmentRepository;
    @Mock private CourseMaterialRepository materialRepository;
    @Mock private BunnyWebhookEventRepository webhookEventRepository;
    @Mock private CourseOutboxEventRepository outboxEventRepository;
    @Mock private CourseVideoProvider courseVideoProvider;
    @Mock private StorageGateway storageGateway;
    @Mock private CourseMaterialProperties materialProperties;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private MentorOwnershipQueryPort mentorOwnershipQueryPort;
    @Mock private CourseAnnouncementNotificationService notificationService;
    @Mock private TimeProvider timeProvider;
    @Mock private VideoStoragePolicy videoStoragePolicy;
    @Mock private VideoPlaybackTokenService videoPlaybackTokenService;

    @InjectMocks private CourseVaultServiceImpl courseVaultService;

    @Test
    void unentitledUserGetsLockedMaterialCode() {
        UUID courseId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Course course = Course.builder().id(courseId).build();
        CourseChapter chapter = CourseChapter.builder().id(UUID.randomUUID()).course(course).build();
        CourseMaterial material = CourseMaterial.builder().id(materialId).chapter(chapter)
                .title("Video").materialType(CourseMaterialType.VIDEO).status(MaterialStatus.READY)
                .storageProviderType(StorageProviderType.OBJECT_STORAGE).build();

        when(materialRepository.findActiveWithCurriculumById(materialId)).thenReturn(Optional.of(material));
        when(courseRepository.findMentorUserIdByCourseId(courseId)).thenReturn(Optional.empty());
        when(enrollmentRepository.findByCourseIdAndStudentUserId(courseId, userId)).thenReturn(Optional.empty());

        BaseException exception = assertThrows(BaseException.class,
                () -> courseVaultService.getPlaybackAuthorization(userId, courseId, materialId, "127.0.0.1"));

        assertEquals(ErrorCode.COURSE_MATERIAL_LOCKED, exception.getErrorCode());
        assertEquals("Tài liệu này chưa được mở khóa", exception.getMessage());
    }
}
