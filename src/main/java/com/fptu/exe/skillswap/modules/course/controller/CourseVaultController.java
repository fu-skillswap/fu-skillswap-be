package com.fptu.exe.skillswap.modules.course.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.course.dto.request.*;
import com.fptu.exe.skillswap.modules.course.dto.response.*;
import com.fptu.exe.skillswap.modules.course.service.CourseProgressService;
import com.fptu.exe.skillswap.modules.course.service.CourseVaultService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Course curriculum", description = "Course chapters and their video or PDF learning materials")
public class CourseVaultController {
    private final CourseVaultService courseVaultService;
    private final CourseProgressService courseProgressService;

    @Operation(summary = "Initialize video upload in a chapter")
    @PostMapping("/me/mentor/courses/{courseId}/chapters/{chapterId}/materials/video/upload-intent")
    public ApiResponse<CourseVideoUploadInitResponse> createVideoUpload(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId, @PathVariable UUID chapterId, @Valid @RequestBody CreateVideoMaterialRequest request) {
        return ApiResponse.success(courseVaultService.createVideoUpload(principal.getId(), courseId, chapterId, request));
    }

    @Operation(summary = "Initialize PDF upload in a chapter")
    @PostMapping("/me/mentor/courses/{courseId}/chapters/{chapterId}/materials/pdf/upload-intent")
    public ApiResponse<CoursePdfUploadInitResponse> createPdfUpload(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId, @PathVariable UUID chapterId, @Valid @RequestBody CreatePdfMaterialUploadRequest request) {
        return ApiResponse.success(courseVaultService.createPdfUpload(principal.getId(), courseId, chapterId, request));
    }

    @Operation(summary = "Confirm PDF upload")
    @PostMapping("/me/mentor/courses/{courseId}/materials/{materialId}/confirm-pdf-upload")
    public ApiResponse<Void> confirmPdfUpload(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId, @PathVariable UUID materialId, @Valid @RequestBody ConfirmCoursePdfUploadRequest request) {
        courseVaultService.confirmPdfUpload(principal.getId(), courseId, materialId, request.objectKey());
        return ApiResponse.success(null);
    }

    @Operation(summary = "List materials in course order")
    @GetMapping("/me/courses/{courseId}/materials")
    public ApiResponse<List<CourseMaterialSummaryResponse>> getCourseMaterials(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId) {
        return ApiResponse.success(courseVaultService.getCourseMaterials(principal.getId(), courseId));
    }

    @Operation(summary = "Get playback URL for a course video")
    @GetMapping("/me/courses/{courseId}/materials/{materialId}/playback")
    public ApiResponse<CourseVideoPlaybackResponse> getPlaybackUrl(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId, @PathVariable UUID materialId, HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String clientIp = forwarded == null || forwarded.isBlank() || "unknown".equalsIgnoreCase(forwarded) ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
        return ApiResponse.success(courseVaultService.getPlaybackAuthorization(principal.getId(), courseId, materialId, clientIp));
    }

    @Operation(summary = "Get signed download URL for a course PDF")
    @GetMapping("/me/courses/{courseId}/materials/{materialId}/download")
    public ApiResponse<CourseMaterialDownloadResponse> getPdfDownload(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId, @PathVariable UUID materialId) {
        return ApiResponse.success(courseVaultService.getPdfDownload(principal.getId(), courseId, materialId));
    }

    @Operation(summary = "Update video learning progress")
    @PutMapping("/me/courses/{courseId}/materials/{materialId}/progress")
    public ApiResponse<Void> updateProgress(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId, @PathVariable UUID materialId, @Valid @RequestBody UpdateCourseMaterialProgressRequest request) {
        courseProgressService.updateMaterialProgress(principal.getId(), courseId, materialId, request.watchedSeconds());
        return ApiResponse.success(null);
    }
}
