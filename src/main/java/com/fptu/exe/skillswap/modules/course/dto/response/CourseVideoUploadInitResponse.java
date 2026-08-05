package com.fptu.exe.skillswap.modules.course.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class CourseVideoUploadInitResponse {
    private UUID materialId;
    private String bunnyLibraryId;
    private String bunnyVideoId;
    private String uploadUrl;
    private String authorizationSignature;
    private long expirationTimestamp;
}
