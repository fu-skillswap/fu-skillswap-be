package com.fptu.exe.skillswap.modules.course.dto.response;

import com.fptu.exe.skillswap.modules.course.domain.CourseMaterialType;
import com.fptu.exe.skillswap.modules.course.domain.MaterialStatus;

import java.util.List;
import java.util.UUID;

public record CourseCurriculumResponse(
        UUID courseId,
        CourseProgressView progress,
        List<Chapter> chapters
) {
    public record CourseProgressView(int overallPercentage, UUID lastStudiedMaterialId) {
    }

    public record Chapter(UUID chapterId, String title, String description, int sortOrder,
                          boolean published, long version, List<Material> materials) {
    }

    public record Material(UUID materialId, String title, CourseMaterialType type, int sortOrder,
                           boolean previewable, boolean published, MaterialStatus status,
                           Integer durationSeconds, String thumbnailUrl, String access,
                           Integer progressPercentage, boolean completed, long version) {
    }
}
