package com.fptu.exe.skillswap.modules.course.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.course.dto.request.CreateVideoMaterialRequest;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseMaterialSummaryResponse;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseVideoPlaybackResponse;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseVideoUploadInitResponse;
import com.fptu.exe.skillswap.modules.course.service.CourseVaultService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Course Vault", description = "Endpoints for managing course materials and videos")
public class CourseVaultController {

    private final CourseVaultService courseVaultService;

    @Operation(summary = "Initialize video upload for a course")
    @PostMapping("/me/mentor/courses/{courseId}/materials/video/create-upload")
    public ApiResponse<CourseVideoUploadInitResponse> createVideoUpload(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @Valid @RequestBody CreateVideoMaterialRequest request) {
        
        CourseVideoUploadInitResponse response = courseVaultService.createVideoUpload(principal.getId(), courseId, request);
        return ApiResponse.success(response);
    }

    @Operation(summary = "Get materials for a course")
    @GetMapping("/me/courses/{courseId}/materials")
    public ApiResponse<List<CourseMaterialSummaryResponse>> getCourseMaterials(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId) {
            
        List<CourseMaterialSummaryResponse> materials = courseVaultService.getCourseMaterials(principal.getId(), courseId);
        return ApiResponse.success(materials);
    }

    @Operation(summary = "Get playback URL for a course video")
    @GetMapping("/me/courses/{courseId}/materials/{materialId}/playback")
    public ApiResponse<CourseVideoPlaybackResponse> getPlaybackUrl(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID materialId,
            jakarta.servlet.http.HttpServletRequest request) {
            
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isEmpty() || "unknown".equalsIgnoreCase(clientIp)) {
            clientIp = request.getRemoteAddr();
        } else {
            clientIp = clientIp.split(",")[0].trim();
        }
            
        CourseVideoPlaybackResponse response = courseVaultService.getPlaybackAuthorization(principal.getId(), courseId, materialId, clientIp);
        return ApiResponse.success(response);
    }
}
