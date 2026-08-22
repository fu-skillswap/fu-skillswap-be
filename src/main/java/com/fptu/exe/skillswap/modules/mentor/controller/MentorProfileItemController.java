package com.fptu.exe.skillswap.modules.mentor.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorAchievementRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorFeaturedProjectRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAchievementResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorFeaturedProjectResponse;
import com.fptu.exe.skillswap.modules.mentor.service.MentorProfileItemService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('MENTEE', 'MENTOR')")
public class MentorProfileItemController {

    private final MentorProfileItemService mentorProfileItemService;

    @Tag(name = "Mentor Profile")
    @Operation(summary = "Lấy danh sách dự án tiêu biểu của mentor hiện tại")
    @GetMapping("/api/me/mentor-projects")
    public ApiResponse<List<MentorFeaturedProjectResponse>> listProjects(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorProfileItemService.listProjects(principal.getPublicId()));
    }

    @Tag(name = "Mentor Profile")
    @Operation(summary = "Tạo dự án tiêu biểu")
    @PostMapping("/api/me/mentor-projects")
    public ResponseEntity<ApiResponse<MentorFeaturedProjectResponse>> createProject(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MentorFeaturedProjectRequest request
    ) {
        ensureAuthenticated(principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(mentorProfileItemService.createProject(principal.getPublicId(), request)));
    }

    @Tag(name = "Mentor Profile")
    @Operation(summary = "Cập nhật dự án tiêu biểu")
    @PutMapping("/api/me/mentor-projects/{projectId}")
    public ApiResponse<MentorFeaturedProjectResponse> updateProject(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId,
            @Valid @RequestBody MentorFeaturedProjectRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorProfileItemService.updateProject(principal.getPublicId(), projectId, request));
    }

    @Tag(name = "Mentor Profile")
    @Operation(summary = "Tạo upload intent cho ảnh dự án tiêu biểu (chưa gắn vào dự án cụ thể)")
    @PostMapping("/api/me/mentor-projects/picture/upload-intents")
    public ResponseEntity<ApiResponse<com.fptu.exe.skillswap.modules.filestorage.dto.response.PublicAssetUploadIntentResponse>> createProjectPictureUploadIntent(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody com.fptu.exe.skillswap.modules.filestorage.dto.request.PublicAssetUploadIntentRequest request
    ) {
        ensureAuthenticated(principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(mentorProfileItemService.createProjectPictureUploadIntent(principal.getPublicId(), request)));
    }

    @Tag(name = "Mentor Profile")
    @Operation(summary = "Tạo upload intent cho ảnh của dự án tiêu biểu cụ thể")
    @PostMapping("/api/me/mentor-projects/{projectId}/picture/upload-intents")
    public ResponseEntity<ApiResponse<com.fptu.exe.skillswap.modules.filestorage.dto.response.PublicAssetUploadIntentResponse>> createProjectPictureUploadIntentForProject(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId,
            @Valid @RequestBody com.fptu.exe.skillswap.modules.filestorage.dto.request.PublicAssetUploadIntentRequest request
    ) {
        ensureAuthenticated(principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(mentorProfileItemService.createProjectPictureUploadIntentForProject(principal.getPublicId(), projectId, request)));
    }

    @Tag(name = "Mentor Profile")
    @Operation(summary = "Xác nhận upload và gắn ảnh vào dự án tiêu biểu")
    @PostMapping("/api/me/mentor-projects/{projectId}/picture/confirm")
    public ApiResponse<MentorFeaturedProjectResponse> confirmProjectPicture(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId,
            @Valid @RequestBody com.fptu.exe.skillswap.modules.mentor.dto.request.MentorProjectPictureConfirmRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorProfileItemService.confirmProjectPicture(principal.getPublicId(), projectId, request.uploadIntentId()));
    }

    @Tag(name = "Mentor Profile")
    @Operation(summary = "Gỡ ảnh khỏi dự án tiêu biểu")
    @DeleteMapping("/api/me/mentor-projects/{projectId}/picture")
    public ApiResponse<MentorFeaturedProjectResponse> removeProjectPicture(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorProfileItemService.removeProjectPicture(principal.getPublicId(), projectId));
    }

    @Tag(name = "Mentor Profile")
    @Operation(summary = "Xóa dự án tiêu biểu")
    @DeleteMapping("/api/me/mentor-projects/{projectId}")
    public ApiResponse<Void> deleteProject(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId
    ) {
        ensureAuthenticated(principal);
        mentorProfileItemService.deleteProject(principal.getPublicId(), projectId);
        return ApiResponse.success(null);
    }

    @Tag(name = "Mentor Profile")
    @Operation(summary = "Lấy danh sách học vấn/giải thưởng của mentor hiện tại")
    @GetMapping("/api/me/mentor-achievements")
    public ApiResponse<List<MentorAchievementResponse>> listAchievements(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorProfileItemService.listAchievements(principal.getPublicId()));
    }

    @Tag(name = "Mentor Profile")
    @Operation(summary = "Tạo học vấn/giải thưởng")
    @PostMapping("/api/me/mentor-achievements")
    public ResponseEntity<ApiResponse<MentorAchievementResponse>> createAchievement(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MentorAchievementRequest request
    ) {
        ensureAuthenticated(principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(mentorProfileItemService.createAchievement(principal.getPublicId(), request)));
    }

    @Tag(name = "Mentor Profile")
    @Operation(summary = "Cập nhật học vấn/giải thưởng")
    @PutMapping("/api/me/mentor-achievements/{achievementId}")
    public ApiResponse<MentorAchievementResponse> updateAchievement(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID achievementId,
            @Valid @RequestBody MentorAchievementRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorProfileItemService.updateAchievement(principal.getPublicId(), achievementId, request));
    }

    @Tag(name = "Mentor Profile")
    @Operation(summary = "Xóa học vấn/giải thưởng")
    @DeleteMapping("/api/me/mentor-achievements/{achievementId}")
    public ApiResponse<Void> deleteAchievement(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID achievementId
    ) {
        ensureAuthenticated(principal);
        mentorProfileItemService.deleteAchievement(principal.getPublicId(), achievementId);
        return ApiResponse.success(null);
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
