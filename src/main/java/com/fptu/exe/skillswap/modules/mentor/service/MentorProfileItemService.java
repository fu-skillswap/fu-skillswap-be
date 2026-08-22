package com.fptu.exe.skillswap.modules.mentor.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MentorProfileItemService {

    private final MentorProfileRepository mentorProfileRepository;
    private final MentorFeaturedProjectRepository mentorFeaturedProjectRepository;
    private final MentorAchievementRepository mentorAchievementRepository;
    private final PublicAssetUploadService publicAssetUploadService;

    @Transactional(readOnly = true)
    public List<MentorFeaturedProjectResponse> listProjects(UUID mentorUserId) {
        requireUserId(mentorUserId);
        return mentorFeaturedProjectRepository.findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(mentorUserId)
                .stream()
                .map(this::mapProject)
                .toList();
    }

    @Transactional
    public MentorFeaturedProjectResponse createProject(UUID mentorUserId, MentorFeaturedProjectRequest request) {
        MentorProfile profile = requireProfile(mentorUserId);
        MentorFeaturedProject project = new MentorFeaturedProject();
        project.setMentorProfile(profile);
        applyProjectRequest(project, mentorUserId, request);
        project.setDisplayOrder(nextProjectOrder(mentorUserId));
        return mapProject(mentorFeaturedProjectRepository.save(project));
    }

    @Transactional
    public MentorFeaturedProjectResponse updateProject(UUID mentorUserId, UUID projectId, MentorFeaturedProjectRequest request) {
        MentorFeaturedProject project = mentorFeaturedProjectRepository.findByIdAndMentorProfileUserId(projectId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy dự án tiêu biểu"));
        applyProjectRequest(project, mentorUserId, request);
        return mapProject(mentorFeaturedProjectRepository.save(project));
    }

    @Transactional
    public void deleteProject(UUID mentorUserId, UUID projectId) {
        MentorFeaturedProject project = mentorFeaturedProjectRepository.findByIdAndMentorProfileUserId(projectId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy dự án tiêu biểu"));
        mentorFeaturedProjectRepository.delete(project);
    }

    @Transactional
    public PublicAssetUploadIntentResponse createProjectPictureUploadIntent(UUID mentorUserId, PublicAssetUploadIntentRequest request) {
        requireUserId(mentorUserId);
        requireProfile(mentorUserId);
        return publicAssetUploadService.createPortfolioImageIntent(mentorUserId, request);
    }

    @Transactional
    public PublicAssetUploadIntentResponse createProjectPictureUploadIntentForProject(UUID mentorUserId, UUID projectId, PublicAssetUploadIntentRequest request) {
        requireUserId(mentorUserId);
        mentorFeaturedProjectRepository.findByIdAndMentorProfileUserId(projectId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy dự án tiêu biểu"));
        return publicAssetUploadService.createPortfolioImageIntent(mentorUserId, request);
    }

    @Transactional
    public MentorFeaturedProjectResponse confirmProjectPicture(UUID mentorUserId, UUID projectId, UUID uploadIntentId) {
        requireUserId(mentorUserId);
        if (uploadIntentId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "uploadIntentId không được để trống");
        }
        MentorFeaturedProject project = mentorFeaturedProjectRepository.findByIdAndMentorProfileUserId(projectId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy dự án tiêu biểu"));
        PublicAssetResponse assetResponse = publicAssetUploadService.confirmPortfolioImage(mentorUserId, uploadIntentId);
        StoredFile storedFile = publicAssetUploadService.requireOwnedPortfolioImage(mentorUserId, assetResponse.assetId());
        project.setPictureFile(storedFile);
        return mapProject(mentorFeaturedProjectRepository.save(project));
    }

    @Transactional
    public MentorFeaturedProjectResponse removeProjectPicture(UUID mentorUserId, UUID projectId) {
        requireUserId(mentorUserId);
        MentorFeaturedProject project = mentorFeaturedProjectRepository.findByIdAndMentorProfileUserId(projectId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy dự án tiêu biểu"));
        project.setPictureFile(null);
        return mapProject(mentorFeaturedProjectRepository.save(project));
    }

    @Transactional(readOnly = true)
    public List<MentorAchievementResponse> listAchievements(UUID mentorUserId) {
        requireUserId(mentorUserId);
        return mentorAchievementRepository.findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(mentorUserId)
                .stream()
                .map(this::mapAchievement)
                .toList();
    }

    @Transactional
    public MentorAchievementResponse createAchievement(UUID mentorUserId, MentorAchievementRequest request) {
        MentorProfile profile = requireProfile(mentorUserId);
        MentorAchievement achievement = new MentorAchievement();
        achievement.setMentorProfile(profile);
        applyAchievementRequest(achievement, request);
        achievement.setDisplayOrder(nextAchievementOrder(mentorUserId));
        return mapAchievement(mentorAchievementRepository.save(achievement));
    }

    @Transactional
    public MentorAchievementResponse updateAchievement(UUID mentorUserId, UUID achievementId, MentorAchievementRequest request) {
        MentorAchievement achievement = mentorAchievementRepository.findByIdAndMentorProfileUserId(achievementId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy học vấn/giải thưởng"));
        applyAchievementRequest(achievement, request);
        return mapAchievement(mentorAchievementRepository.save(achievement));
    }

    @Transactional
    public void deleteAchievement(UUID mentorUserId, UUID achievementId) {
        MentorAchievement achievement = mentorAchievementRepository.findByIdAndMentorProfileUserId(achievementId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy học vấn/giải thưởng"));
        mentorAchievementRepository.delete(achievement);
    }

    private void applyProjectRequest(MentorFeaturedProject project, UUID mentorUserId, MentorFeaturedProjectRequest request) {
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Dữ liệu dự án không được để trống");
        }
        project.setTitle(clean(request.title(), "Tên dự án"));
        project.setContent(cleanNullable(request.content()));
        project.setProjectDescription(cleanNullable(request.projectDescription()));
        project.setLiveDemoUrl(cleanNullable(request.liveDemoUrl()));
        if (request.pictureAssetId() != null) {
            StoredFile picture = publicAssetUploadService.requireOwnedPortfolioImage(mentorUserId, request.pictureAssetId());
            project.setPictureFile(picture);
        }
    }

    private void applyAchievementRequest(MentorAchievement achievement, MentorAchievementRequest request) {
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Dữ liệu học vấn/giải thưởng không được để trống");
        }
        achievement.setTitle(clean(request.title(), "Tiêu đề"));
        achievement.setAwardDescription(cleanNullable(request.awardDescription()));
        achievement.setAchievedAt(request.achievedAt());
        achievement.setProductHeader(cleanNullable(request.productHeader()));
        achievement.setProductDescription(cleanNullable(request.productDescription()));
        achievement.setDemoUrl(cleanNullable(request.demoUrl()));
    }

    private MentorProfile requireProfile(UUID mentorUserId) {
        requireUserId(mentorUserId);
        return mentorProfileRepository.findWithUserByUserId(mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.BAD_REQUEST, "Cần tạo mentor profile trước"));
    }

    private int nextProjectOrder(UUID mentorUserId) {
        return mentorFeaturedProjectRepository.findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(mentorUserId).size();
    }

    private int nextAchievementOrder(UUID mentorUserId) {
        return mentorAchievementRepository.findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(mentorUserId).size();
    }

    private MentorFeaturedProjectResponse mapProject(MentorFeaturedProject project) {
        return MentorFeaturedProjectResponse.builder()
                .id(project.getId())
                .title(project.getTitle())
                .pictureUrl(project.getPictureFile() == null ? null : project.getPictureFile().getPublicUrl())
                .content(project.getContent())
                .projectDescription(project.getProjectDescription())
                .liveDemoUrl(project.getLiveDemoUrl())
                .displayOrder(project.getDisplayOrder())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    private MentorAchievementResponse mapAchievement(MentorAchievement achievement) {
        return MentorAchievementResponse.builder()
                .id(achievement.getId())
                .title(achievement.getTitle())
                .awardDescription(achievement.getAwardDescription())
                .achievedAt(achievement.getAchievedAt())
                .productHeader(achievement.getProductHeader())
                .productDescription(achievement.getProductDescription())
                .demoUrl(achievement.getDemoUrl())
                .displayOrder(achievement.getDisplayOrder())
                .createdAt(achievement.getCreatedAt())
                .updatedAt(achievement.getUpdatedAt())
                .build();
    }

    private String clean(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, label + " không được để trống");
        }
        return value.trim();
    }

    private String cleanNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void requireUserId(UUID userId) {
        if (userId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
