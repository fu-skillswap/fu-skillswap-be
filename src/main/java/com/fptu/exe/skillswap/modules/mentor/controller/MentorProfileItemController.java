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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("!hasRole('ADMIN') and !hasRole('SYSTEM_ADMIN')")
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
    @Operation(summary = "Upload ảnh dự án tiêu biểu")
    @PutMapping(path = "/api/me/mentor-projects/{projectId}/picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MentorFeaturedProjectResponse> uploadProjectPicture(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId,
            @RequestPart("file") MultipartFile file
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorProfileItemService.uploadProjectPicture(principal.getPublicId(), projectId, file));
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
