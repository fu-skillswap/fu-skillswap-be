package com.fptu.exe.skillswap.modules.course.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.course.dto.request.*;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseCurriculumResponse;
import com.fptu.exe.skillswap.modules.course.service.CourseCurriculumService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Course curriculum", description = "Course -> Chapter -> Video or PDF")
public class CourseCurriculumController {
    private final CourseCurriculumService curriculumService;

    @Operation(summary = "Get course curriculum")
    @GetMapping("/me/courses/{courseId}/curriculum")
    public ApiResponse<CourseCurriculumResponse> getCurriculum(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId) {
        return ApiResponse.success(curriculumService.getCurriculum(principal.getId(), courseId));
    }
    @Operation(summary = "Create course chapter")
    @PostMapping("/me/mentor/courses/{courseId}/chapters")
    public ApiResponse<UUID> createChapter(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId, @Valid @RequestBody CreateCourseChapterRequest request) {
        return ApiResponse.success(curriculumService.createChapter(principal.getId(), courseId, request).getId());
    }
    @Operation(summary = "Update course chapter")
    @PutMapping("/me/mentor/courses/{courseId}/chapters/{chapterId}")
    public ApiResponse<Void> updateChapter(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId, @PathVariable UUID chapterId, @Valid @RequestBody UpdateCourseChapterRequest request) {
        curriculumService.updateChapter(principal.getId(), courseId, chapterId, request); return ApiResponse.success(null);
    }
    @Operation(summary = "Delete an empty course chapter")
    @DeleteMapping("/me/mentor/courses/{courseId}/chapters/{chapterId}")
    public ApiResponse<Void> deleteChapter(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId, @PathVariable UUID chapterId) {
        curriculumService.deleteEmptyChapter(principal.getId(), courseId, chapterId); return ApiResponse.success(null);
    }
    @Operation(summary = "Reorder course chapters")
    @PutMapping("/me/mentor/courses/{courseId}/chapters/order")
    public ApiResponse<Void> reorderChapters(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId, @Valid @RequestBody ReorderCurriculumRequest request) {
        curriculumService.reorderChapters(principal.getId(), courseId, request); return ApiResponse.success(null);
    }
    @Operation(summary = "Update a course material")
    @PutMapping("/me/mentor/courses/{courseId}/materials/{materialId}")
    public ApiResponse<Void> updateMaterial(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId, @PathVariable UUID materialId, @Valid @RequestBody UpdateCourseMaterialRequest request) {
        curriculumService.updateMaterial(principal.getId(), courseId, materialId, request); return ApiResponse.success(null);
    }
    @Operation(summary = "Reorder materials in a chapter")
    @PutMapping("/me/mentor/courses/{courseId}/chapters/{chapterId}/materials/order")
    public ApiResponse<Void> reorderMaterials(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId, @PathVariable UUID chapterId, @Valid @RequestBody ReorderCurriculumRequest request) {
        curriculumService.reorderMaterials(principal.getId(), courseId, chapterId, request); return ApiResponse.success(null);
    }
    @Operation(summary = "Delete a course material")
    @DeleteMapping("/me/mentor/courses/{courseId}/materials/{materialId}")
    public ApiResponse<Void> deleteMaterial(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId, @PathVariable UUID materialId) {
        curriculumService.deleteMaterial(principal.getId(), courseId, materialId); return ApiResponse.success(null);
    }
}
