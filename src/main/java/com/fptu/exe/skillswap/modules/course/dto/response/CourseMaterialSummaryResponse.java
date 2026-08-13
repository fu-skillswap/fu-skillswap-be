package com.fptu.exe.skillswap.modules.course.dto.response;

import com.fptu.exe.skillswap.modules.course.domain.MaterialStatus;
import com.fptu.exe.skillswap.modules.course.domain.MaterialType;
import com.fptu.exe.skillswap.modules.course.domain.StorageProviderType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CourseMaterialSummaryResponse {
    private UUID resourceId;
    private UUID lectureId;
    private String title;
    private MaterialType materialType;
    private StorageProviderType storageProviderType;
    private MaterialStatus status;
    private Integer durationSeconds;
    private String thumbnailUrl;
    private Instant uploadedAt;
    
    private boolean available;
    private String lockedReason;
}
