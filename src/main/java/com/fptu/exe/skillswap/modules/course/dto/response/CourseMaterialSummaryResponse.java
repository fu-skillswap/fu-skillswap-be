package com.fptu.exe.skillswap.modules.course.dto.response;

import com.fptu.exe.skillswap.modules.course.domain.MaterialStatus;
import com.fptu.exe.skillswap.modules.course.domain.CourseMaterialType;
import com.fptu.exe.skillswap.modules.course.domain.StorageProviderType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CourseMaterialSummaryResponse {
    private UUID materialId;
    private UUID chapterId;
    private String title;
    private CourseMaterialType materialType;
    private StorageProviderType storageProviderType;
    private MaterialStatus status;
    private Integer durationSeconds;
    private String thumbnailUrl;
    private Instant uploadedAt;
    
    private boolean available;
    private String lockedReason;
}
