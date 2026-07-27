package com.fptu.exe.skillswap.modules.mentor.dto.response;
import com.fptu.exe.skillswap.modules.mentor.domain.*;
import java.time.LocalDateTime;
import java.util.UUID;
public record MentorServiceResourceResponse(UUID resourceId, String title, String description, MentorServiceResourceType resourceType,
    MentorServiceResourceVisibility visibility, String contentType, long sizeBytes, String presentationMode,
    boolean canDownload, String downloadRestrictionCode, Integer version, LocalDateTime createdAt) {}
