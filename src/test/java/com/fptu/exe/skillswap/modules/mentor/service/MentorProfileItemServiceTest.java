package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.filestorage.domain.FilePurpose;
import com.fptu.exe.skillswap.modules.filestorage.domain.StoredFile;
import com.fptu.exe.skillswap.modules.filestorage.dto.request.PublicAssetUploadIntentRequest;
import com.fptu.exe.skillswap.modules.filestorage.dto.response.PublicAssetResponse;
import com.fptu.exe.skillswap.modules.filestorage.dto.response.PublicAssetUploadIntentResponse;
import com.fptu.exe.skillswap.modules.filestorage.service.PublicAssetUploadService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorAchievement;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorFeaturedProject;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorAchievementRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorFeaturedProjectRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAchievementResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorFeaturedProjectResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorAchievementRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorFeaturedProjectRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MentorProfileItemServiceTest {

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @Mock
    private MentorFeaturedProjectRepository mentorFeaturedProjectRepository;

    @Mock
    private MentorAchievementRepository mentorAchievementRepository;

    @Mock
    private PublicAssetUploadService publicAssetUploadService;

    private MentorProfileItemService service;

    private UUID mentorUserId;
    private MentorProfile profile;

    @BeforeEach
    void setUp() {
        service = new MentorProfileItemService(
                mentorProfileRepository,
                mentorFeaturedProjectRepository,
                mentorAchievementRepository,
                publicAssetUploadService
        );
        mentorUserId = UUID.randomUUID();
        profile = new MentorProfile();
        profile.setUserId(mentorUserId);
    }

    @Test
    void listProjects_Success() {
        MentorFeaturedProject project = MentorFeaturedProject.builder()
                .id(UUID.randomUUID())
                .mentorProfile(profile)
                .title("Project Alpha")
                .content("Content")
                .projectDescription("Description")
                .liveDemoUrl("https://alpha.example.com")
                .displayOrder(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(mentorFeaturedProjectRepository.findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(mentorUserId))
                .thenReturn(List.of(project));

        List<MentorFeaturedProjectResponse> results = service.listProjects(mentorUserId);
        assertEquals(1, results.size());
        assertEquals("Project Alpha", results.get(0).title());
    }

    @Test
    void createProject_WithoutPictureAssetId_Success() {
        when(mentorProfileRepository.findWithUserByUserId(mentorUserId)).thenReturn(Optional.of(profile));
        when(mentorFeaturedProjectRepository.findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(mentorUserId))
                .thenReturn(List.of());
        when(mentorFeaturedProjectRepository.save(any(MentorFeaturedProject.class)))
                .thenAnswer(inv -> {
                    MentorFeaturedProject p = inv.getArgument(0);
                    p.setId(UUID.randomUUID());
                    return p;
                });

        MentorFeaturedProjectRequest request = new MentorFeaturedProjectRequest(
                "New Project", "Content", "Desc", "https://demo.com", null
        );

        MentorFeaturedProjectResponse response = service.createProject(mentorUserId, request);
        assertNotNull(response);
        assertEquals("New Project", response.title());
        assertNull(response.pictureUrl());
    }

    @Test
    void createProject_WithPictureAssetId_Success() {
        UUID assetId = UUID.randomUUID();
        StoredFile file = StoredFile.builder()
                .id(assetId)
                .purpose(FilePurpose.PORTFOLIO)
                .publicUrl("https://cdn.skillswap.asia/portfolio/pic.jpg")
                .build();

        when(mentorProfileRepository.findWithUserByUserId(mentorUserId)).thenReturn(Optional.of(profile));
        when(publicAssetUploadService.requireOwnedPortfolioImage(mentorUserId, assetId)).thenReturn(file);
        when(mentorFeaturedProjectRepository.findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(mentorUserId))
                .thenReturn(List.of());
        when(mentorFeaturedProjectRepository.save(any(MentorFeaturedProject.class)))
                .thenAnswer(inv -> {
                    MentorFeaturedProject p = inv.getArgument(0);
                    p.setId(UUID.randomUUID());
                    return p;
                });

        MentorFeaturedProjectRequest request = new MentorFeaturedProjectRequest(
                "New Project", "Content", "Desc", "https://demo.com", assetId
        );

        MentorFeaturedProjectResponse response = service.createProject(mentorUserId, request);
        assertNotNull(response);
        assertEquals("New Project", response.title());
        assertEquals("https://cdn.skillswap.asia/portfolio/pic.jpg", response.pictureUrl());
    }

    @Test
    void createProjectPictureUploadIntent_Success() {
        when(mentorProfileRepository.findWithUserByUserId(mentorUserId)).thenReturn(Optional.of(profile));
        PublicAssetUploadIntentRequest req = new PublicAssetUploadIntentRequest("photo.png", "image/png");
        PublicAssetUploadIntentResponse intentResponse = new PublicAssetUploadIntentResponse(
                UUID.randomUUID(), "https://r2.example.com/upload", LocalDateTime.now().plusMinutes(15), Map.of("Content-Type", "image/png")
        );
        when(publicAssetUploadService.createPortfolioImageIntent(mentorUserId, req)).thenReturn(intentResponse);

        PublicAssetUploadIntentResponse res = service.createProjectPictureUploadIntent(mentorUserId, req);
        assertEquals(intentResponse.uploadIntentId(), res.uploadIntentId());
        assertEquals(intentResponse.uploadUrl(), res.uploadUrl());
    }

    @Test
    void createProjectPictureUploadIntentForProject_NotFound_ThrowsException() {
        UUID projectId = UUID.randomUUID();
        when(mentorFeaturedProjectRepository.findByIdAndMentorProfileUserId(projectId, mentorUserId))
                .thenReturn(Optional.empty());

        PublicAssetUploadIntentRequest req = new PublicAssetUploadIntentRequest("photo.png", "image/png");
        BaseException ex = assertThrows(BaseException.class,
                () -> service.createProjectPictureUploadIntentForProject(mentorUserId, projectId, req));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void confirmProjectPicture_Success() {
        UUID projectId = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        MentorFeaturedProject project = MentorFeaturedProject.builder()
                .id(projectId)
                .mentorProfile(profile)
                .title("Alpha")
                .build();

        StoredFile storedFile = StoredFile.builder()
                .id(assetId)
                .purpose(FilePurpose.PORTFOLIO)
                .publicUrl("https://cdn.skillswap.asia/portfolio/pic.png")
                .build();

        when(mentorFeaturedProjectRepository.findByIdAndMentorProfileUserId(projectId, mentorUserId))
                .thenReturn(Optional.of(project));
        when(publicAssetUploadService.confirmPortfolioImage(mentorUserId, intentId))
                .thenReturn(new PublicAssetResponse(assetId, "https://cdn.skillswap.asia/portfolio/pic.png", "image/png", 1024L));
        when(publicAssetUploadService.requireOwnedPortfolioImage(mentorUserId, assetId))
                .thenReturn(storedFile);
        when(mentorFeaturedProjectRepository.save(any(MentorFeaturedProject.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        MentorFeaturedProjectResponse response = service.confirmProjectPicture(mentorUserId, projectId, intentId);
        assertNotNull(response);
        assertEquals("https://cdn.skillswap.asia/portfolio/pic.png", response.pictureUrl());
    }

    @Test
    void removeProjectPicture_Success() {
        UUID projectId = UUID.randomUUID();
        StoredFile storedFile = StoredFile.builder()
                .id(UUID.randomUUID())
                .publicUrl("https://cdn.skillswap.asia/portfolio/pic.png")
                .build();

        MentorFeaturedProject project = MentorFeaturedProject.builder()
                .id(projectId)
                .mentorProfile(profile)
                .title("Alpha")
                .pictureFile(storedFile)
                .build();

        when(mentorFeaturedProjectRepository.findByIdAndMentorProfileUserId(projectId, mentorUserId))
                .thenReturn(Optional.of(project));
        when(mentorFeaturedProjectRepository.save(any(MentorFeaturedProject.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        MentorFeaturedProjectResponse response = service.removeProjectPicture(mentorUserId, projectId);
        assertNotNull(response);
        assertNull(response.pictureUrl());
    }

    @Test
    void deleteProject_Success() {
        UUID projectId = UUID.randomUUID();
        MentorFeaturedProject project = MentorFeaturedProject.builder()
                .id(projectId)
                .mentorProfile(profile)
                .title("Alpha")
                .build();

        when(mentorFeaturedProjectRepository.findByIdAndMentorProfileUserId(projectId, mentorUserId))
                .thenReturn(Optional.of(project));

        service.deleteProject(mentorUserId, projectId);
        verify(mentorFeaturedProjectRepository).delete(project);
    }

    @Test
    void achievementCrud_Success() {
        when(mentorProfileRepository.findWithUserByUserId(mentorUserId)).thenReturn(Optional.of(profile));
        when(mentorAchievementRepository.findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(mentorUserId))
                .thenReturn(List.of());
        when(mentorAchievementRepository.save(any(MentorAchievement.class)))
                .thenAnswer(inv -> {
                    MentorAchievement a = inv.getArgument(0);
                    a.setId(UUID.randomUUID());
                    return a;
                });

        MentorAchievementRequest req = new MentorAchievementRequest(
                "Top 1 Hackathon", "First place award", LocalDate.of(2026, 1, 1),
                "App", "App Description", "https://demo.com", null
        );

        MentorAchievementResponse res = service.createAchievement(mentorUserId, req);
        assertEquals("Top 1 Hackathon", res.title());
        assertEquals("First place award", res.awardDescription());
        assertNull(res.pictureUrl());
    }

    @Test
    void createAchievement_WithPictureAssetId_Success() {
        UUID assetId = UUID.randomUUID();
        StoredFile file = StoredFile.builder()
                .id(assetId)
                .purpose(FilePurpose.PORTFOLIO)
                .publicUrl("https://cdn.skillswap.asia/portfolio/cert.jpg")
                .build();

        when(mentorProfileRepository.findWithUserByUserId(mentorUserId)).thenReturn(Optional.of(profile));
        when(publicAssetUploadService.requireOwnedPortfolioImage(mentorUserId, assetId)).thenReturn(file);
        when(mentorAchievementRepository.findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(mentorUserId))
                .thenReturn(List.of());
        when(mentorAchievementRepository.save(any(MentorAchievement.class)))
                .thenAnswer(inv -> {
                    MentorAchievement a = inv.getArgument(0);
                    a.setId(UUID.randomUUID());
                    return a;
                });

        MentorAchievementRequest req = new MentorAchievementRequest(
                "Top 1 Hackathon", "First place award", LocalDate.of(2026, 1, 1),
                "App", "App Description", "https://demo.com", assetId
        );

        MentorAchievementResponse res = service.createAchievement(mentorUserId, req);
        assertEquals("Top 1 Hackathon", res.title());
        assertEquals("https://cdn.skillswap.asia/portfolio/cert.jpg", res.pictureUrl());
    }

    @Test
    void createAchievementPictureUploadIntent_Success() {
        when(mentorProfileRepository.findWithUserByUserId(mentorUserId)).thenReturn(Optional.of(profile));
        PublicAssetUploadIntentRequest req = new PublicAssetUploadIntentRequest("cert.png", "image/png");
        PublicAssetUploadIntentResponse intentResponse = new PublicAssetUploadIntentResponse(
                UUID.randomUUID(), "https://r2.example.com/upload", LocalDateTime.now().plusMinutes(15), Map.of("Content-Type", "image/png")
        );
        when(publicAssetUploadService.createPortfolioImageIntent(mentorUserId, req)).thenReturn(intentResponse);

        PublicAssetUploadIntentResponse res = service.createAchievementPictureUploadIntent(mentorUserId, req);
        assertEquals(intentResponse.uploadIntentId(), res.uploadIntentId());
        assertEquals(intentResponse.uploadUrl(), res.uploadUrl());
    }

    @Test
    void confirmAchievementPicture_Success() {
        UUID achievementId = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        MentorAchievement achievement = MentorAchievement.builder()
                .id(achievementId)
                .mentorProfile(profile)
                .title("Certified Kubernetes Admin")
                .build();

        StoredFile storedFile = StoredFile.builder()
                .id(assetId)
                .purpose(FilePurpose.PORTFOLIO)
                .publicUrl("https://cdn.skillswap.asia/portfolio/cert.png")
                .build();

        when(mentorAchievementRepository.findByIdAndMentorProfileUserId(achievementId, mentorUserId))
                .thenReturn(Optional.of(achievement));
        when(publicAssetUploadService.confirmPortfolioImage(mentorUserId, intentId))
                .thenReturn(new PublicAssetResponse(assetId, "https://cdn.skillswap.asia/portfolio/cert.png", "image/png", 2048L));
        when(publicAssetUploadService.requireOwnedPortfolioImage(mentorUserId, assetId))
                .thenReturn(storedFile);
        when(mentorAchievementRepository.save(any(MentorAchievement.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        MentorAchievementResponse response = service.confirmAchievementPicture(mentorUserId, achievementId, intentId);
        assertNotNull(response);
        assertEquals("https://cdn.skillswap.asia/portfolio/cert.png", response.pictureUrl());
    }

    @Test
    void removeAchievementPicture_Success() {
        UUID achievementId = UUID.randomUUID();
        StoredFile storedFile = StoredFile.builder()
                .id(UUID.randomUUID())
                .publicUrl("https://cdn.skillswap.asia/portfolio/cert.png")
                .build();

        MentorAchievement achievement = MentorAchievement.builder()
                .id(achievementId)
                .mentorProfile(profile)
                .title("Certified Kubernetes Admin")
                .pictureFile(storedFile)
                .build();

        when(mentorAchievementRepository.findByIdAndMentorProfileUserId(achievementId, mentorUserId))
                .thenReturn(Optional.of(achievement));
        when(mentorAchievementRepository.save(any(MentorAchievement.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        MentorAchievementResponse response = service.removeAchievementPicture(mentorUserId, achievementId);
        assertNotNull(response);
        assertNull(response.pictureUrl());
    }
}
