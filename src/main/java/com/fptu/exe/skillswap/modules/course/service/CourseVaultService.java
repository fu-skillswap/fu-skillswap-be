package com.fptu.exe.skillswap.modules.course.service;

import com.fptu.exe.skillswap.modules.course.domain.BunnyWebhookEvent;
import com.fptu.exe.skillswap.modules.course.dto.CourseVideoWebhook;
import com.fptu.exe.skillswap.modules.course.dto.request.CreateVideoMaterialRequest;
import com.fptu.exe.skillswap.modules.course.dto.request.CreatePdfMaterialUploadRequest;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseVideoPlaybackResponse;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseVideoUploadInitResponse;

import java.util.UUID;

public interface CourseVaultService {
    
    CourseVideoUploadInitResponse createVideoUpload(UUID mentorUserId, UUID courseId, UUID chapterId, CreateVideoMaterialRequest request);
    com.fptu.exe.skillswap.modules.course.dto.response.CoursePdfUploadInitResponse createPdfUpload(UUID mentorUserId, UUID courseId, UUID chapterId, CreatePdfMaterialUploadRequest request);
    void confirmPdfUpload(UUID mentorUserId, UUID courseId, UUID materialId, String objectKey);
    com.fptu.exe.skillswap.modules.course.dto.response.CourseMaterialDownloadResponse getPdfDownload(UUID userId, UUID courseId, UUID materialId);
    
    CourseVideoPlaybackResponse getPlaybackAuthorization(UUID userId, UUID courseId, UUID materialId, String clientIp);
    
    BunnyWebhookEvent saveWebhookAuditLog(String signature, String externalEventId, CourseVideoWebhook payload);
    
    java.util.List<java.util.UUID> claimWebhookEvents(int limit);

    void processWebhookEventIdempotent(java.util.UUID eventId);

    void markWebhookEventFailed(java.util.UUID eventId, Throwable cause);

    java.util.List<java.util.UUID> claimCourseOutboxEvents(int limit);

    void processCourseOutboxEvent(java.util.UUID eventId);

    void markCourseOutboxEventFailed(java.util.UUID eventId, Throwable cause);
    
    void deleteMaterial(UUID userId, UUID courseId, UUID materialId);
    
    java.util.List<com.fptu.exe.skillswap.modules.course.dto.response.CourseMaterialSummaryResponse> getCourseMaterials(UUID userId, UUID courseId);
}
