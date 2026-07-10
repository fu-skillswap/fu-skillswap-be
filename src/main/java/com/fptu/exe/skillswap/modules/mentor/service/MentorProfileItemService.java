package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.modules.filestorage.domain.FilePurpose;
import com.fptu.exe.skillswap.modules.filestorage.domain.StoredFile;
import com.fptu.exe.skillswap.modules.filestorage.repository.StoredFileRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MentorProfileItemService {

    private static final Set<String> PROJECT_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_PROJECT_IMAGE_BYTES = 5L * 1024L * 1024L;

    private final MentorProfileRepository mentorProfileRepository;
    private final MentorFeaturedProjectRepository mentorFeaturedProjectRepository;
    private final MentorAchievementRepository mentorAchievementRepository;
    private final StoredFileRepository storedFileRepository;
    private final UserRepository userRepository;
    private final ObjectProvider<StorageGateway> r2StorageProvider;

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
        applyProjectRequest(project, request);
        project.setDisplayOrder(nextProjectOrder(mentorUserId));
        return mapProject(mentorFeaturedProjectRepository.save(project));
    }

    @Transactional
    public MentorFeaturedProjectResponse updateProject(UUID mentorUserId, UUID projectId, MentorFeaturedProjectRequest request) {
        MentorFeaturedProject project = mentorFeaturedProjectRepository.findByIdAndMentorProfileUserId(projectId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy dự án tiêu biểu"));
        applyProjectRequest(project, request);
        return mapProject(mentorFeaturedProjectRepository.save(project));
    }

    @Transactional
    public void deleteProject(UUID mentorUserId, UUID projectId) {
        MentorFeaturedProject project = mentorFeaturedProjectRepository.findByIdAndMentorProfileUserId(projectId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy dự án tiêu biểu"));
        mentorFeaturedProjectRepository.delete(project);
    }

    @Transactional
    public MentorFeaturedProjectResponse uploadProjectPicture(UUID mentorUserId, UUID projectId, MultipartFile file) {
        MentorFeaturedProject project = mentorFeaturedProjectRepository.findByIdAndMentorProfileUserId(projectId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy dự án tiêu biểu"));
        validateProjectImage(file);
        StorageGateway storageService = r2StorageProvider.getIfAvailable();
        if (storageService == null) {
            throw new BaseException(ErrorCode.STORAGE_ERROR, "Hệ thống chưa cấu hình R2 để upload ảnh dự án");
        }
        User user = userRepository.findById(mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));
        StorageGateway.StorageUploadResult uploadResult = storageService.uploadFile(file, "mentor-projects/" + mentorUserId);
        StoredFile storedFile = storedFileRepository.save(StoredFile.builder()
                .owner(user)
                .purpose(FilePurpose.PORTFOLIO)
                .originalName(sanitizeFilename(file.getOriginalFilename()))
                .storageProvider("R2")
                .storageKey(uploadResult.objectKey())
                .publicUrl(uploadResult.publicUrl())
                .mimeType(canonicalizeContentType(file.getContentType()))
                .sizeBytes(file.getSize())
                .build());
        project.setPictureFile(storedFile);
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

    private void applyProjectRequest(MentorFeaturedProject project, MentorFeaturedProjectRequest request) {
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Dữ liệu dự án không được để trống");
        }
        project.setTitle(clean(request.title(), "Tên dự án"));
        project.setContent(cleanNullable(request.content()));
        project.setProjectDescription(cleanNullable(request.projectDescription()));
        project.setLiveDemoUrl(cleanNullable(request.liveDemoUrl()));
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

    private void validateProjectImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Ảnh dự án không được để trống");
        }
        if (file.getSize() > MAX_PROJECT_IMAGE_BYTES) {
            throw new BaseException(ErrorCode.PAYLOAD_TOO_LARGE, "Ảnh dự án không được vượt quá 5MB");
        }
        String contentType = canonicalizeContentType(file.getContentType());
        if (!PROJECT_IMAGE_TYPES.contains(contentType)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Ảnh dự án chỉ hỗ trợ JPG, PNG hoặc WEBP");
        }
    }

    private String canonicalizeContentType(String contentType) {
        String normalized = contentType == null ? "" : contentType.trim().toLowerCase();
        if ("image/jpg".equals(normalized) || "image/pjpeg".equals(normalized)) {
            return "image/jpeg";
        }
        return normalized;
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

    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return null;
        }
        return filename.replace("/", "").replace("\\", "").trim();
    }

    private void requireUserId(UUID userId) {
        if (userId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
