package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.filestorage.port.PublicAssetUploadPort;
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
    private final PublicAssetUploadPort publicAssetUploadPort;

    @Transactional(readOnly = true)
    public List<MentorFeaturedProjectResponse> listProjects(UUID mentorUserId) {
        requireUserId(mentorUserId);
        return mentorFeaturedProjectRepository.findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(mentorUserId)
                .stream()
                .map(project -> mapProject(project, mentorUserId))
                .toList();
    }

    @Transactional
    public MentorFeaturedProjectResponse createProject(UUID mentorUserId, MentorFeaturedProjectRequest request) {
        MentorProfile profile = requireProfile(mentorUserId);
        MentorFeaturedProject project = new MentorFeaturedProject();
        project.setMentorProfile(profile);
        applyProjectRequest(project, mentorUserId, request);
        project.setDisplayOrder(nextProjectOrder(mentorUserId));
        return mapProject(mentorFeaturedProjectRepository.save(project), mentorUserId);
    }

    @Transactional
    public MentorFeaturedProjectResponse updateProject(UUID mentorUserId, UUID projectId, MentorFeaturedProjectRequest request) {
        MentorFeaturedProject project = mentorFeaturedProjectRepository.findByIdAndMentorProfileUserId(projectId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy dự án tiêu biểu"));
        applyProjectRequest(project, mentorUserId, request);
        return mapProject(mentorFeaturedProjectRepository.save(project), mentorUserId);
    }

    @Transactional
    public void deleteProject(UUID mentorUserId, UUID projectId) {
        MentorFeaturedProject project = mentorFeaturedProjectRepository.findByIdAndMentorProfileUserId(projectId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy dự án tiêu biểu"));
        mentorFeaturedProjectRepository.delete(project);
    }

    @Transactional
    public PublicAssetUploadPort.UploadIntent createProjectPictureUploadIntent(UUID mentorUserId, PublicAssetUploadPort.UploadRequest request) {
        requireUserId(mentorUserId);
        requireProfile(mentorUserId);
        return publicAssetUploadPort.createPortfolioImageIntent(mentorUserId, request);
    }

    @Transactional
    public PublicAssetUploadPort.UploadIntent createProjectPictureUploadIntentForProject(UUID mentorUserId, UUID projectId, PublicAssetUploadPort.UploadRequest request) {
        requireUserId(mentorUserId);
        mentorFeaturedProjectRepository.findByIdAndMentorProfileUserId(projectId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy dự án tiêu biểu"));
        return publicAssetUploadPort.createPortfolioImageIntent(mentorUserId, request);
    }

    @Transactional
    public MentorFeaturedProjectResponse confirmProjectPicture(UUID mentorUserId, UUID projectId, UUID uploadIntentId) {
        requireUserId(mentorUserId);
        if (uploadIntentId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "uploadIntentId không được để trống");
        }
        MentorFeaturedProject project = mentorFeaturedProjectRepository.findByIdAndMentorProfileUserId(projectId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy dự án tiêu biểu"));
        PublicAssetUploadPort.FileAssetMetadata asset = publicAssetUploadPort.confirmPortfolioImage(mentorUserId, uploadIntentId);
        project.setPictureFileId(asset.assetId());
        return mapProject(mentorFeaturedProjectRepository.save(project), mentorUserId);
    }

    @Transactional
    public MentorFeaturedProjectResponse removeProjectPicture(UUID mentorUserId, UUID projectId) {
        requireUserId(mentorUserId);
        MentorFeaturedProject project = mentorFeaturedProjectRepository.findByIdAndMentorProfileUserId(projectId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy dự án tiêu biểu"));
        project.setPictureFileId(null);
        return mapProject(mentorFeaturedProjectRepository.save(project), mentorUserId);
    }

    @Transactional(readOnly = true)
    public List<MentorAchievementResponse> listAchievements(UUID mentorUserId) {
        requireUserId(mentorUserId);
        return mentorAchievementRepository.findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(mentorUserId)
                .stream()
                .map(achievement -> mapAchievement(achievement, mentorUserId))
                .toList();
    }

    @Transactional
    public MentorAchievementResponse createAchievement(UUID mentorUserId, MentorAchievementRequest request) {
        MentorProfile profile = requireProfile(mentorUserId);
        MentorAchievement achievement = new MentorAchievement();
        achievement.setMentorProfile(profile);
        applyAchievementRequest(achievement, mentorUserId, request);
        achievement.setDisplayOrder(nextAchievementOrder(mentorUserId));
        return mapAchievement(mentorAchievementRepository.save(achievement), mentorUserId);
    }

    @Transactional
    public MentorAchievementResponse updateAchievement(UUID mentorUserId, UUID achievementId, MentorAchievementRequest request) {
        MentorAchievement achievement = mentorAchievementRepository.findByIdAndMentorProfileUserId(achievementId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy học vấn/giải thưởng"));
        applyAchievementRequest(achievement, mentorUserId, request);
        return mapAchievement(mentorAchievementRepository.save(achievement), mentorUserId);
    }

    @Transactional
    public void deleteAchievement(UUID mentorUserId, UUID achievementId) {
        MentorAchievement achievement = mentorAchievementRepository.findByIdAndMentorProfileUserId(achievementId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy học vấn/giải thưởng"));
        mentorAchievementRepository.delete(achievement);
    }

    @Transactional(readOnly = true)
    public PublicAssetUploadPort.UploadIntent createAchievementPictureUploadIntent(UUID mentorUserId, PublicAssetUploadPort.UploadRequest request) {
        requireProfile(mentorUserId);
        return publicAssetUploadPort.createPortfolioImageIntent(mentorUserId, request);
    }

    @Transactional(readOnly = true)
    public PublicAssetUploadPort.UploadIntent createAchievementPictureUploadIntentForAchievement(UUID mentorUserId, UUID achievementId, PublicAssetUploadPort.UploadRequest request) {
        requireUserId(mentorUserId);
        mentorAchievementRepository.findByIdAndMentorProfileUserId(achievementId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy học vấn/giải thưởng"));
        return publicAssetUploadPort.createPortfolioImageIntent(mentorUserId, request);
    }

    @Transactional
    public MentorAchievementResponse confirmAchievementPicture(UUID mentorUserId, UUID achievementId, UUID uploadIntentId) {
        requireUserId(mentorUserId);
        MentorAchievement achievement = mentorAchievementRepository.findByIdAndMentorProfileUserId(achievementId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy học vấn/giải thưởng"));
        PublicAssetUploadPort.FileAssetMetadata asset = publicAssetUploadPort.confirmPortfolioImage(mentorUserId, uploadIntentId);
        achievement.setPictureFileId(asset.assetId());
        return mapAchievement(mentorAchievementRepository.save(achievement), mentorUserId);
    }

    @Transactional
    public MentorAchievementResponse removeAchievementPicture(UUID mentorUserId, UUID achievementId) {
        requireUserId(mentorUserId);
        MentorAchievement achievement = mentorAchievementRepository.findByIdAndMentorProfileUserId(achievementId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy học vấn/giải thưởng"));
        achievement.setPictureFileId(null);
        return mapAchievement(mentorAchievementRepository.save(achievement), mentorUserId);
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
            PublicAssetUploadPort.FileAssetMetadata picture = publicAssetUploadPort.requireOwnedPortfolioImage(mentorUserId, request.pictureAssetId());
            project.setPictureFileId(picture.assetId());
        }
    }

    private void applyAchievementRequest(MentorAchievement achievement, UUID mentorUserId, MentorAchievementRequest request) {
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Dữ liệu học vấn/giải thưởng không được để trống");
        }
        achievement.setTitle(clean(request.title(), "Tiêu đề"));
        achievement.setAwardDescription(cleanNullable(request.awardDescription()));
        achievement.setAchievedAt(request.achievedAt());
        achievement.setProductHeader(cleanNullable(request.productHeader()));
        achievement.setProductDescription(cleanNullable(request.productDescription()));
        achievement.setDemoUrl(cleanNullable(request.demoUrl()));
        if (request.pictureAssetId() != null) {
            PublicAssetUploadPort.FileAssetMetadata picture = publicAssetUploadPort.requireOwnedPortfolioImage(mentorUserId, request.pictureAssetId());
            achievement.setPictureFileId(picture.assetId());
        }
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

    private MentorFeaturedProjectResponse mapProject(MentorFeaturedProject project, UUID mentorUserId) {
        return MentorFeaturedProjectResponse.builder()
                .id(project.getId())
                .title(project.getTitle())
                .pictureUrl(assetUrl(mentorUserId, project.getPictureFileId()))
                .content(project.getContent())
                .projectDescription(project.getProjectDescription())
                .liveDemoUrl(project.getLiveDemoUrl())
                .displayOrder(project.getDisplayOrder())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    private MentorAchievementResponse mapAchievement(MentorAchievement achievement, UUID mentorUserId) {
        return MentorAchievementResponse.builder()
                .id(achievement.getId())
                .title(achievement.getTitle())
                .pictureUrl(assetUrl(mentorUserId, achievement.getPictureFileId()))
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

    private String assetUrl(UUID ownerId, UUID assetId) {
        if (assetId == null) return null;
        PublicAssetUploadPort.FileAssetMetadata metadata = publicAssetUploadPort.requireOwnedPortfolioImage(ownerId, assetId);
        return metadata.publicUrl();
    }

    private void requireUserId(UUID userId) {
        if (userId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
