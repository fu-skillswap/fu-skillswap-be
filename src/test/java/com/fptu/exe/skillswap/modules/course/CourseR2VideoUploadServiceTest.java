package com.fptu.exe.skillswap.modules.course;

import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.modules.course.domain.Course;
import com.fptu.exe.skillswap.modules.course.domain.CourseChapter;
import com.fptu.exe.skillswap.modules.course.domain.CourseMaterial;
import com.fptu.exe.skillswap.modules.course.domain.CourseMaterialType;
import com.fptu.exe.skillswap.modules.course.domain.MaterialStatus;
import com.fptu.exe.skillswap.modules.course.domain.StorageProviderType;
import com.fptu.exe.skillswap.modules.course.dto.request.CreateR2VideoUploadIntentRequest;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseR2VideoUploadIntentResponse;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseVideoPlaybackResponse;
import com.fptu.exe.skillswap.modules.course.repository.CourseChapterRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseMaterialRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseOutboxEventRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseRepository;
import com.fptu.exe.skillswap.modules.course.repository.BunnyWebhookEventRepository;
import com.fptu.exe.skillswap.modules.course.service.CourseAnnouncementNotificationService;
import com.fptu.exe.skillswap.modules.course.service.CourseVaultServiceImpl;
import com.fptu.exe.skillswap.modules.course.port.VideoStorageProvider;
import com.fptu.exe.skillswap.infrastructure.config.CourseMaterialProperties;
import com.fptu.exe.skillswap.modules.mentor.port.MentorOwnershipQueryPort;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourseR2VideoUploadServiceTest {

    @Mock private CourseRepository courseRepository;
    @Mock private CourseChapterRepository chapterRepository;
    @Mock private CourseEnrollmentRepository enrollmentRepository;
    @Mock private CourseMaterialRepository materialRepository;
    @Mock private BunnyWebhookEventRepository webhookEventRepository;
    @Mock private CourseOutboxEventRepository outboxEventRepository;
    @Mock private VideoStorageProvider videoStorageProvider;
    @Mock private StorageGateway storageGateway;
    @Mock private CourseMaterialProperties materialProperties;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private MentorOwnershipQueryPort mentorOwnershipQueryPort;
    @Mock private CourseAnnouncementNotificationService announcementNotificationService;
    @Mock private TimeProvider timeProvider;
    @InjectMocks private CourseVaultServiceImpl service;

    private final UUID mentorId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID chapterId = UUID.randomUUID();
    private final UUID materialId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-09-04T03:00:00Z");
    private Course course;
    private CourseChapter chapter;

    @BeforeEach
    void setUp() {
        course = Course.builder().id(courseId).mentorUserId(mentorId).build();
        chapter = CourseChapter.builder().id(chapterId).course(course).build();
        when(timeProvider.instant()).thenReturn(now);
        when(videoStorageProvider.activeProviderType()).thenReturn(StorageProviderType.OBJECT_STORAGE);
        when(chapterRepository.findById(chapterId)).thenReturn(Optional.of(chapter));
        when(courseRepository.findMentorUserIdByCourseId(courseId)).thenReturn(Optional.of(mentorId));
        when(mentorOwnershipQueryPort.isOwnedBy(mentorId, mentorId)).thenReturn(true);
        when(materialRepository.findByChapterIdAndDeletedAtIsNullOrderBySortOrderAsc(chapterId))
                .thenReturn(Collections.emptyList());
        when(materialRepository.save(any(CourseMaterial.class))).thenAnswer(invocation -> {
            CourseMaterial material = invocation.getArgument(0);
            material.setId(materialId);
            return material;
        });
    }

    @Test
    void validUploadIntentReturnsProviderNeutralContract() {
        Instant expiresAt = now.plusSeconds(900);
        when(videoStorageProvider.createUploadIntent(any())).thenReturn(new VideoStorageProvider.UploadIntent(
                StorageProviderType.OBJECT_STORAGE, null, null,
                "course-materials/videos/" + mentorId + "/" + materialId + ".mp4",
                "https://r2.example/upload", null, expiresAt, "video/mp4",
                java.util.Map.of("Content-Type", "video/mp4"), false));

        CourseR2VideoUploadIntentResponse response = service.createR2VideoUploadIntent(
                mentorId, courseId, chapterId, request(50_000_000L));

        assertThat(response.assetId()).isEqualTo(materialId);
        assertThat(response.uploadIntentId()).isEqualTo(materialId);
        assertThat(response.uploadUrl()).isEqualTo("https://r2.example/upload");
        assertThat(response.status()).isEqualTo(MaterialStatus.UPLOADING);
        assertThat(response.requiredHeaders()).containsEntry("Content-Type", "video/mp4");
        ArgumentCaptor<CourseMaterial> captor = ArgumentCaptor.forClass(CourseMaterial.class);
        verify(materialRepository).save(captor.capture());
        CourseMaterial saved = captor.getValue();
        assertThat(saved.getStorageProviderType()).isEqualTo(StorageProviderType.OBJECT_STORAGE);
        assertThat(saved.getVideoObjectKey()).isEqualTo("course-materials/videos/" + mentorId + "/" + materialId + ".mp4");
    }

    @Test
    void invalidContentTypeIsRejectedBeforeCreatingMaterial() {
        doThrow(new BaseException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "MVP chỉ hỗ trợ video/mp4"))
                .when(videoStorageProvider).validateUploadRequest(anyString(), anyLong());

        assertThatThrownBy(() -> service.createR2VideoUploadIntent(
                mentorId, courseId, chapterId, new CreateR2VideoUploadIntentRequest(
                        "Video", "video.mov", "video/quicktime", 50_000L, 1, false, false)))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        verify(materialRepository, never()).save(any());
    }

    @Test
    void oversizedVideoIsRejectedBeforeCreatingMaterial() {
        doThrow(new BaseException(ErrorCode.PAYLOAD_TOO_LARGE, "Video quá lớn"))
                .when(videoStorageProvider).validateUploadRequest(anyString(), anyLong());

        assertThatThrownBy(() -> service.createR2VideoUploadIntent(
                mentorId, courseId, chapterId, request(600_000_000L)))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE);
        verify(materialRepository, never()).save(any());
    }

    @Test
    void expiredUploadIntentIsRejectedWithoutReadingStorage() {
        CourseMaterial material = r2Material(now.minusSeconds(1));
        when(materialRepository.findActiveWithCurriculumById(materialId)).thenReturn(Optional.of(material));

        doThrow(new BaseException(ErrorCode.BAD_REQUEST, "Video upload intent đã hết hạn; hãy tạo intent mới"))
                .when(videoStorageProvider).confirmUpload(any());
        assertThatThrownBy(() -> service.confirmR2VideoUpload(mentorId, courseId, materialId))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("đã hết hạn");
        verify(storageGateway, never()).headObject(anyString());
    }

    @Test
    void missingR2ObjectIsRejectedAndMaterialRemainsUploading() {
        CourseMaterial material = r2Material(now.plusSeconds(900));
        when(materialRepository.findActiveWithCurriculumById(materialId)).thenReturn(Optional.of(material));
        when(videoStorageProvider.confirmUpload(any()))
                .thenThrow(new BaseException(ErrorCode.BAD_REQUEST, "File upload chưa tồn tại trên storage"));

        assertThatThrownBy(() -> service.confirmR2VideoUpload(mentorId, courseId, materialId))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("chưa tồn tại");
        assertThat(material.getStatus()).isEqualTo(MaterialStatus.UPLOADING);
    }

    @Test
    void successfulConfirmationValidatesMetadataAndMarksMaterialReady() {
        CourseMaterial material = r2Material(now.plusSeconds(900));
        when(materialRepository.findActiveWithCurriculumById(materialId)).thenReturn(Optional.of(material));
        when(videoStorageProvider.confirmUpload(any())).thenReturn(
                new VideoStorageProvider.UploadConfirmation(true, "video/mp4", 50_000_000L));
        when(materialRepository.countByChapterCourseIdAndDeletedAtIsNullAndIsPublishedTrue(courseId)).thenReturn(1L);

        service.confirmR2VideoUpload(mentorId, courseId, materialId);

        assertThat(material.getStatus()).isEqualTo(MaterialStatus.READY);
        assertThat(material.getFileSizeBytes()).isEqualTo(50_000_000L);
        assertThat(material.getVideoContentType()).isEqualTo("video/mp4");
        assertThat(material.getUploadExpiresAt()).isNull();
    }

    @Test
    void r2PlaybackAuthorizationReturnsVpsUrlForPreviewableReadyVideo() {
        CourseMaterial material = r2Material(now.plusSeconds(900));
        material.setStatus(MaterialStatus.READY);
        material.setPreviewable(true);
        when(materialRepository.findActiveWithCurriculumById(materialId)).thenReturn(Optional.of(material));
        VideoStorageProvider.PlaybackUrl playback = new VideoStorageProvider.PlaybackUrl(
                "/stream/videos/" + materialId + ".mp4?token=short-lived-token", now.plusSeconds(300));
        when(videoStorageProvider.generatePlaybackUrl(any(), anyString())).thenReturn(playback);

        CourseVideoPlaybackResponse response = service.getPlaybackAuthorization(mentorId, courseId, materialId, "127.0.0.1");

        assertThat(response.getPlaybackUrl()).isEqualTo("/stream/videos/" + materialId + ".mp4?token=short-lived-token");
        assertThat(response.getExpiresAt()).isEqualTo(playback.expiresAt());
    }

    @Test
    void r2PlaybackAuthorizationRejectsUserWithoutCourseAccess() {
        CourseMaterial material = r2Material(now.plusSeconds(900));
        material.setStatus(MaterialStatus.READY);
        material.setPreviewable(false);
        when(materialRepository.findActiveWithCurriculumById(materialId)).thenReturn(Optional.of(material));
        when(courseRepository.findMentorUserIdByCourseId(courseId)).thenReturn(Optional.of(UUID.randomUUID()));
        when(enrollmentRepository.findByCourseIdAndStudentUserId(courseId, mentorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPlaybackAuthorization(mentorId, courseId, materialId, "127.0.0.1"))
                .isInstanceOf(com.fptu.exe.skillswap.shared.exception.BaseException.class)
                .extracting("errorCode")
                .isEqualTo(com.fptu.exe.skillswap.shared.exception.ErrorCode.COURSE_MATERIAL_LOCKED);
        verify(videoStorageProvider, never()).generatePlaybackUrl(any(), anyString());
    }

    private CreateR2VideoUploadIntentRequest request(long sizeBytes) {
        return new CreateR2VideoUploadIntentRequest("Spring Boot", "lesson.mp4", "video/mp4",
                sizeBytes, 1, false, false);
    }

    private CourseMaterial r2Material(Instant expiresAt) {
        return CourseMaterial.builder()
                .id(materialId)
                .chapter(chapter)
                .title("Spring Boot")
                .materialType(CourseMaterialType.VIDEO)
                .storageProviderType(StorageProviderType.OBJECT_STORAGE)
                .status(MaterialStatus.UPLOADING)
                .videoObjectKey("course-materials/videos/" + mentorId + "/" + materialId + ".mp4")
                .videoContentType("video/mp4")
                .uploadExpiresAt(expiresAt)
                .uploadedBy(mentorId)
                .uploadedAt(now)
                .build();
    }
}
