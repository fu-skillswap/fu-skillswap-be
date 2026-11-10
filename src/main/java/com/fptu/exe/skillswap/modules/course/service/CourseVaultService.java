package com.fptu.exe.skillswap.modules.course.service;

import com.fptu.exe.skillswap.modules.course.domain.BunnyWebhookEvent;
import com.fptu.exe.skillswap.modules.course.dto.CourseVideoWebhook;
import com.fptu.exe.skillswap.modules.course.dto.request.CreatePdfMaterialUploadRequest;
import com.fptu.exe.skillswap.modules.course.dto.request.CreateR2VideoUploadIntentRequest;
import com.fptu.exe.skillswap.modules.course.dto.request.CreateVideoMaterialRequest;
import com.fptu.exe.skillswap.modules.course.dto.request.CreateYouTubeVideoMaterialRequest;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseMaterialDownloadResponse;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseMaterialSummaryResponse;
import com.fptu.exe.skillswap.modules.course.dto.response.CoursePdfUploadInitResponse;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseR2VideoUploadIntentResponse;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseVideoPlaybackResponse;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseVideoUploadInitResponse;

import java.util.List;
import java.util.UUID;

public interface CourseVaultService {

    CourseVideoPlaybackResponse createYouTubeVideoMaterial(UUID mentorUserId, UUID courseId, UUID chapterId, CreateYouTubeVideoMaterialRequest request);

    CourseVideoUploadInitResponse createVideoUpload(UUID mentorUserId, UUID courseId, UUID chapterId, CreateVideoMaterialRequest request);

    CourseR2VideoUploadIntentResponse createR2VideoUploadIntent(UUID mentorUserId, UUID courseId, UUID chapterId, CreateR2VideoUploadIntentRequest request);

    void confirmR2VideoUpload(UUID mentorUserId, UUID courseId, UUID materialId, Integer durationSeconds);

    CoursePdfUploadInitResponse createPdfUpload(UUID mentorUserId, UUID courseId, UUID chapterId, CreatePdfMaterialUploadRequest request);

    void confirmPdfUpload(UUID mentorUserId, UUID courseId, UUID materialId, String objectKey);

    CourseMaterialDownloadResponse getPdfDownload(UUID userId, UUID courseId, UUID materialId);

    CourseVideoPlaybackResponse getPlaybackAuthorization(UUID userId, UUID courseId, UUID materialId, String clientIp);

    BunnyWebhookEvent saveWebhookAuditLog(String signature, String externalEventId, CourseVideoWebhook payload);

    List<UUID> claimWebhookEvents(int limit);

    void processWebhookEventIdempotent(UUID eventId);

    void markWebhookEventFailed(UUID eventId, Throwable cause);

    List<UUID> claimCourseOutboxEvents(int limit);

    void processCourseOutboxEvent(UUID eventId);

    void markCourseOutboxEventFailed(UUID eventId, Throwable cause);

    void deleteMaterial(UUID userId, UUID courseId, UUID materialId);

    List<CourseMaterialSummaryResponse> getCourseMaterials(UUID userId, UUID courseId);
}
