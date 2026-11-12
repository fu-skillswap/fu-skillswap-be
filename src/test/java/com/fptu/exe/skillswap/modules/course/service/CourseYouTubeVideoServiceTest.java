package com.fptu.exe.skillswap.modules.course.service;

import com.fptu.exe.skillswap.infrastructure.config.CourseMaterialProperties;
import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.modules.course.domain.*;
import com.fptu.exe.skillswap.modules.course.dto.request.CreateYouTubeVideoMaterialRequest;
import com.fptu.exe.skillswap.modules.course.dto.request.UpdateCourseMaterialRequest;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseCurriculumResponse;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseMaterialSummaryResponse;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseVideoPlaybackResponse;
import com.fptu.exe.skillswap.modules.course.port.VideoStorageProvider;
import com.fptu.exe.skillswap.modules.course.repository.*;
import com.fptu.exe.skillswap.modules.mentor.port.MentorOwnershipQueryPort;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseYouTubeVideoServiceTest {

    @Mock private CourseRepository courseRepository;
    @Mock private CourseChapterRepository chapterRepository;
    @Mock private CourseEnrollmentRepository enrollmentRepository;
    @Mock private CourseMaterialRepository materialRepository;
    @Mock private CourseMaterialProgressRepository materialProgressRepository;
    @Mock private CourseProgressRepository courseProgressRepository;
    @Mock private BunnyWebhookEventRepository webhookEventRepository;
    @Mock private CourseOutboxEventRepository outboxEventRepository;
    @Mock private VideoStorageProvider videoStorageProvider;
    @Mock private StorageGateway storageGateway;
    @Mock private CourseMaterialProperties materialProperties;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private MentorOwnershipQueryPort mentorOwnershipQueryPort;
    @Mock private TimeProvider timeProvider;

    @InjectMocks private CourseVaultServiceImpl vaultService;

    private CourseCurriculumService curriculumService;

    private final UUID mentorUserId = UUID.randomUUID();
    private final UUID studentUserId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID chapterId = UUID.randomUUID();
    private final UUID materialId = UUID.randomUUID();

    private Course testCourse;
    private CourseChapter testChapter;

    @BeforeEach
    void setUp() {
        testCourse = Course.builder()
                .id(courseId)
                .mentorUserId(mentorUserId)
                .title("Test Course")
                .totalMaterials(0)
                .build();

        testChapter = CourseChapter.builder()
                .id(chapterId)
                .course(testCourse)
                .title("Chapter 1")
                .sortOrder(1)
                .isPublished(true)
                .build();

        curriculumService = new CourseCurriculumService(
                courseRepository,
                chapterRepository,
                materialRepository,
                materialProgressRepository,
                courseProgressRepository,
                enrollmentRepository,
                vaultService,
                mentorOwnershipQueryPort
        );
    }

    @Test
    @DisplayName("createYouTubeVideoMaterial creates READY material with extracted video ID and thumbnail")
    void createYouTubeVideoMaterial_success() {
        when(chapterRepository.findById(chapterId)).thenReturn(Optional.of(testChapter));
        when(courseRepository.findMentorUserIdByCourseId(courseId)).thenReturn(Optional.of(mentorUserId));
        when(mentorOwnershipQueryPort.isActiveOwner(mentorUserId, mentorUserId)).thenReturn(true);
        when(materialRepository.findByChapterIdAndDeletedAtIsNullOrderBySortOrderAsc(chapterId)).thenReturn(Collections.emptyList());
        when(timeProvider.instant()).thenReturn(Instant.parse("2026-11-10T10:00:00Z"));

        CreateYouTubeVideoMaterialRequest request = new CreateYouTubeVideoMaterialRequest(
                "Introduction to Spring Boot",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                600,
                1,
                false,
                true
        );

        when(materialRepository.save(any(CourseMaterial.class))).thenAnswer(invocation -> {
            CourseMaterial m = invocation.getArgument(0);
            m.setId(materialId);
            return m;
        });

        CourseVideoPlaybackResponse response = vaultService.createYouTubeVideoMaterial(mentorUserId, courseId, chapterId, request);

        assertThat(response).isNotNull();
        assertThat(response.getMaterialId()).isEqualTo(materialId);
        assertThat(response.getTitle()).isEqualTo("Introduction to Spring Boot");
        assertThat(response.getYoutubeVideoId()).isEqualTo("dQw4w9WgXcQ");
        assertThat(response.getEmbedUrl()).isEqualTo("https://www.youtube.com/embed/dQw4w9WgXcQ");
        assertThat(response.getPlaybackUrl()).isEqualTo("https://www.youtube.com/embed/dQw4w9WgXcQ");
        assertThat(response.getStorageProviderType()).isEqualTo(StorageProviderType.YOUTUBE);
        assertThat(response.getThumbnailUrl()).isEqualTo("https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg");
        assertThat(response.getDurationSeconds()).isEqualTo(600);
        assertThat(response.getExpiresAt()).isNull();

        verifyNoInteractions(videoStorageProvider);
    }

    @Test
    @DisplayName("getPlaybackAuthorization for YouTube video returns embed URL without calling videoStorageProvider")
    void getPlaybackAuthorization_youTubeVideo_returnsEmbedUrl() {
        CourseMaterial material = CourseMaterial.builder()
                .id(materialId)
                .chapter(testChapter)
                .title("YouTube Lesson")
                .materialType(CourseMaterialType.VIDEO)
                .storageProviderType(StorageProviderType.YOUTUBE)
                .status(MaterialStatus.READY)
                .isPublished(true)
                .isPreviewable(true)
                .youtubeVideoId("dQw4w9WgXcQ")
                .youtubeUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
                .thumbnailUrl("https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg")
                .durationSeconds(420)
                .build();

        when(materialRepository.findActiveWithCurriculumById(materialId)).thenReturn(Optional.of(material));
        when(courseRepository.findMentorUserIdByCourseId(courseId)).thenReturn(Optional.of(mentorUserId));
        when(mentorOwnershipQueryPort.isActiveOwner(mentorUserId, studentUserId)).thenReturn(false);

        CourseVideoPlaybackResponse response = vaultService.getPlaybackAuthorization(studentUserId, courseId, materialId, "127.0.0.1");

        assertThat(response.getYoutubeVideoId()).isEqualTo("dQw4w9WgXcQ");
        assertThat(response.getEmbedUrl()).isEqualTo("https://www.youtube.com/embed/dQw4w9WgXcQ");
        assertThat(response.getPlaybackUrl()).isEqualTo("https://www.youtube.com/embed/dQw4w9WgXcQ");
        assertThat(response.getStorageProviderType()).isEqualTo(StorageProviderType.YOUTUBE);
        assertThat(response.getExpiresAt()).isNull();

        verifyNoInteractions(videoStorageProvider);
    }

    @Test
    @DisplayName("getCurriculum hides youtubeVideoId, embedUrl, and YouTube thumbnailUrl when access is LOCKED")
    void getCurriculum_lockedMaterial_hidesYouTubeInfo() {
        CourseMaterial material = CourseMaterial.builder()
                .id(materialId)
                .chapter(testChapter)
                .title("Locked YouTube Lesson")
                .materialType(CourseMaterialType.VIDEO)
                .storageProviderType(StorageProviderType.YOUTUBE)
                .status(MaterialStatus.READY)
                .sortOrder(1)
                .isPublished(true)
                .isPreviewable(false)
                .youtubeVideoId("dQw4w9WgXcQ")
                .youtubeUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
                .thumbnailUrl("https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg")
                .durationSeconds(500)
                .build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(testCourse));
        when(mentorOwnershipQueryPort.isActiveOwner(mentorUserId, studentUserId)).thenReturn(false);
        when(enrollmentRepository.findByCourseIdAndStudentUserId(courseId, studentUserId)).thenReturn(Optional.empty());
        when(chapterRepository.findByCourseIdAndIsPublishedTrueOrderBySortOrderAsc(courseId)).thenReturn(List.of(testChapter));
        when(materialProgressRepository.findByStudentUserIdAndCourseId(studentUserId, courseId)).thenReturn(Collections.emptyList());
        when(materialRepository.findByChapterIdAndDeletedAtIsNullOrderBySortOrderAsc(chapterId)).thenReturn(List.of(material));
        when(courseProgressRepository.findByStudentUserIdAndCourseId(studentUserId, courseId)).thenReturn(Optional.empty());

        CourseCurriculumResponse response = curriculumService.getCurriculum(studentUserId, courseId);

        assertThat(response.chapters()).hasSize(1);
        CourseCurriculumResponse.Material matRes = response.chapters().get(0).materials().get(0);
        assertThat(matRes.access()).isEqualTo("LOCKED");
        assertThat(matRes.youtubeVideoId()).isNull();
        assertThat(matRes.embedUrl()).isNull();
        assertThat(matRes.thumbnailUrl()).isNull();
        assertThat(matRes.storageProviderType()).isEqualTo(StorageProviderType.YOUTUBE);
    }

    @Test
    @DisplayName("getCourseMaterials hides YouTube thumbnail when locked and throws 404 when course missing")
    void getCourseMaterials_lockedMasksThumbnailAndMissingCourseThrows404() {
        CourseMaterial material = CourseMaterial.builder()
                .id(materialId)
                .chapter(testChapter)
                .title("Locked YouTube Lesson")
                .materialType(CourseMaterialType.VIDEO)
                .storageProviderType(StorageProviderType.YOUTUBE)
                .status(MaterialStatus.READY)
                .sortOrder(1)
                .isPublished(true)
                .isPreviewable(false)
                .youtubeVideoId("dQw4w9WgXcQ")
                .thumbnailUrl("https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg")
                .build();

        when(courseRepository.findMentorUserIdByCourseId(courseId)).thenReturn(Optional.of(mentorUserId));
        when(mentorOwnershipQueryPort.isActiveOwner(mentorUserId, studentUserId)).thenReturn(false);
        when(enrollmentRepository.findByCourseIdAndStudentUserId(courseId, studentUserId)).thenReturn(Optional.empty());
        when(materialRepository.findActiveByCourseIdOrderByCurriculum(courseId)).thenReturn(List.of(material));

        List<CourseMaterialSummaryResponse> list = vaultService.getCourseMaterials(studentUserId, courseId);
        assertThat(list).hasSize(1);
        assertThat(list.getFirst().isAvailable()).isFalse();
        assertThat(list.getFirst().getThumbnailUrl()).isNull();

        UUID missingCourseId = UUID.randomUUID();
        when(courseRepository.findMentorUserIdByCourseId(missingCourseId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> vaultService.getCourseMaterials(studentUserId, missingCourseId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("deleteMaterial immediately releases sortOrder and sets deletedAt")
    void deleteMaterial_releasesSortOrderAndMarksDeletedAt() {
        Instant now = Instant.parse("2026-11-11T10:00:00Z");
        CourseMaterial material = CourseMaterial.builder()
                .id(materialId)
                .chapter(testChapter)
                .title("To Delete")
                .materialType(CourseMaterialType.VIDEO)
                .storageProviderType(StorageProviderType.YOUTUBE)
                .status(MaterialStatus.READY)
                .sortOrder(1)
                .build();

        when(materialRepository.findActiveWithCurriculumById(materialId)).thenReturn(Optional.of(material));
        when(courseRepository.findMentorUserIdByCourseId(courseId)).thenReturn(Optional.of(mentorUserId));
        when(mentorOwnershipQueryPort.isActiveOwner(mentorUserId, mentorUserId)).thenReturn(true);
        when(materialRepository.findMinSortOrderByChapterId(chapterId)).thenReturn(1);
        when(timeProvider.instant()).thenReturn(now);

        vaultService.deleteMaterial(mentorUserId, courseId, materialId);

        assertThat(material.getStatus()).isEqualTo(MaterialStatus.DELETING);
        assertThat(material.getDeletedAt()).isEqualTo(now);
        assertThat(material.getSortOrder()).isLessThan(-10000);
        verify(outboxEventRepository).save(any(CourseOutboxEvent.class));
    }

    @Test
    @DisplayName("getCurriculum returns youtubeVideoId, embedUrl, and thumbnailUrl when access is AVAILABLE")
    void getCurriculum_availableMaterial_returnsYouTubeInfo() {
        CourseMaterial material = CourseMaterial.builder()
                .id(materialId)
                .chapter(testChapter)
                .title("Available YouTube Lesson")
                .materialType(CourseMaterialType.VIDEO)
                .storageProviderType(StorageProviderType.YOUTUBE)
                .status(MaterialStatus.READY)
                .sortOrder(1)
                .isPublished(true)
                .isPreviewable(true) // previewable -> AVAILABLE
                .youtubeVideoId("dQw4w9WgXcQ")
                .youtubeUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
                .thumbnailUrl("https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg")
                .durationSeconds(500)
                .build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(testCourse));
        when(mentorOwnershipQueryPort.isActiveOwner(mentorUserId, studentUserId)).thenReturn(false);
        when(enrollmentRepository.findByCourseIdAndStudentUserId(courseId, studentUserId)).thenReturn(Optional.empty());
        when(chapterRepository.findByCourseIdAndIsPublishedTrueOrderBySortOrderAsc(courseId)).thenReturn(List.of(testChapter));
        when(materialProgressRepository.findByStudentUserIdAndCourseId(studentUserId, courseId)).thenReturn(Collections.emptyList());
        when(materialRepository.findByChapterIdAndDeletedAtIsNullOrderBySortOrderAsc(chapterId)).thenReturn(List.of(material));
        when(courseProgressRepository.findByStudentUserIdAndCourseId(studentUserId, courseId)).thenReturn(Optional.empty());

        CourseCurriculumResponse response = curriculumService.getCurriculum(studentUserId, courseId);

        assertThat(response.chapters()).hasSize(1);
        CourseCurriculumResponse.Material matRes = response.chapters().get(0).materials().get(0);
        assertThat(matRes.access()).isEqualTo("AVAILABLE");
        assertThat(matRes.youtubeVideoId()).isEqualTo("dQw4w9WgXcQ");
        assertThat(matRes.embedUrl()).isEqualTo("https://www.youtube.com/embed/dQw4w9WgXcQ");
        assertThat(matRes.thumbnailUrl()).isEqualTo("https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg");
    }

    @Test
    @DisplayName("updateMaterial updates YouTube URL and duration for YouTube video")
    void updateMaterial_updatesYouTubeVideoFields() {
        CourseMaterial material = CourseMaterial.builder()
                .id(materialId)
                .chapter(testChapter)
                .title("Original Title")
                .materialType(CourseMaterialType.VIDEO)
                .storageProviderType(StorageProviderType.YOUTUBE)
                .status(MaterialStatus.READY)
                .sortOrder(1)
                .isPublished(true)
                .isPreviewable(false)
                .youtubeVideoId("oldVideoId1")
                .youtubeUrl("https://www.youtube.com/watch?v=oldVideoId1")
                .durationSeconds(300)
                .version(1L)
                .build();

        when(materialRepository.findActiveWithCurriculumById(materialId)).thenReturn(Optional.of(material));
        when(mentorOwnershipQueryPort.isActiveOwner(mentorUserId, mentorUserId)).thenReturn(true);

        UpdateCourseMaterialRequest updateRequest = new UpdateCourseMaterialRequest(
                "Updated Title",
                true,
                true,
                1L,
                "https://youtu.be/dQw4w9WgXcQ",
                750
        );

        CourseMaterial updated = curriculumService.updateMaterial(mentorUserId, courseId, materialId, updateRequest);

        assertThat(updated.getTitle()).isEqualTo("Updated Title");
        assertThat(updated.getYoutubeVideoId()).isEqualTo("dQw4w9WgXcQ");
        assertThat(updated.getYoutubeUrl()).isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertThat(updated.getThumbnailUrl()).isEqualTo("https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg");
        assertThat(updated.getDurationSeconds()).isEqualTo(750);
    }
}
