package com.fptu.exe.skillswap.modules.course.service;

import com.fptu.exe.skillswap.infrastructure.bunny.client.BunnyVideoClient;
import com.fptu.exe.skillswap.infrastructure.bunny.config.BunnyStreamProperties;
import com.fptu.exe.skillswap.infrastructure.bunny.dto.BunnyCreateVideoResponse;
import com.fptu.exe.skillswap.infrastructure.bunny.dto.BunnyWebhookPayload;
import com.fptu.exe.skillswap.modules.course.domain.BunnyWebhookEvent;
import com.fptu.exe.skillswap.modules.course.domain.Course;
import com.fptu.exe.skillswap.modules.course.domain.CourseEnrollment;
import com.fptu.exe.skillswap.modules.course.domain.CourseMaterial;
import com.fptu.exe.skillswap.modules.course.domain.CourseSessionStatus;
import com.fptu.exe.skillswap.modules.course.domain.MaterialAccessScope;
import com.fptu.exe.skillswap.modules.course.domain.MaterialStatus;
import com.fptu.exe.skillswap.modules.course.domain.MaterialType;
import com.fptu.exe.skillswap.modules.course.domain.StorageProviderType;
import com.fptu.exe.skillswap.modules.course.dto.request.CreateVideoMaterialRequest;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseVideoPlaybackResponse;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseVideoUploadInitResponse;
import com.fptu.exe.skillswap.modules.course.repository.BunnyWebhookEventRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseMaterialRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseVaultServiceImpl implements CourseVaultService {

    private final CourseRepository courseRepository;
    private final CourseSessionRepository sessionRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final CourseMaterialRepository materialRepository;
    private final BunnyWebhookEventRepository webhookEventRepository;
    private final BunnyVideoClient bunnyVideoClient;
    private final BunnyStreamProperties bunnyProperties;
    private final com.fptu.exe.skillswap.modules.course.repository.CourseOutboxEventRepository outboxEventRepository;

    @Override
    @Transactional
    public CourseVideoUploadInitResponse createVideoUpload(UUID mentorUserId, UUID courseId, CreateVideoMaterialRequest request) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));

        if (!course.getMentorProfile().getUserId().equals(mentorUserId)) {
            throw new AccessDeniedException("Only course mentor can upload materials");
        }

        String libraryId = bunnyProperties.getLibraryId();
        BunnyCreateVideoResponse bunnyResponse = bunnyVideoClient.createVideo(request.getTitle());

        // Standard 2 hours TTL for upload
        long expiresAt = Instant.now().plusSeconds(2 * 3600).getEpochSecond();
        String uploadSignature = bunnyVideoClient.generateDirectUploadSignature(bunnyResponse.getGuid(), expiresAt);

        CourseMaterial material = CourseMaterial.builder()
                .course(course)
                .courseSession(request.getCourseSessionId() != null ?
                        sessionRepository.getReferenceById(request.getCourseSessionId()) : null)
                .title(request.getTitle())
                .materialType(MaterialType.VIDEO)
                .storageProviderType(StorageProviderType.BUNNY_VIDEO)
                .status(MaterialStatus.UPLOADING)
                .accessScope(request.getCourseSessionId() != null ? MaterialAccessScope.SESSION_LEVEL : MaterialAccessScope.COURSE_LEVEL)
                .bunnyLibraryId(libraryId)
                .bunnyVideoId(bunnyResponse.getGuid())
                .uploadedBy(mentorUserId)
                .uploadedAt(Instant.now())
                .build();

        materialRepository.save(material);

        return CourseVideoUploadInitResponse.builder()
                .materialId(material.getId())
                .bunnyLibraryId(libraryId)
                .bunnyVideoId(bunnyResponse.getGuid())
                .uploadUrl(String.format("https://video.bunnycdn.com/library/%s/videos/%s", libraryId, bunnyResponse.getGuid()))
                .authorizationSignature(uploadSignature)
                .expirationTimestamp(expiresAt)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CourseVideoPlaybackResponse getPlaybackAuthorization(UUID userId, UUID courseId, UUID materialId) {
        CourseMaterial material = materialRepository.findById(materialId)
                .orElseThrow(() -> new IllegalArgumentException("Material not found"));

        if (!material.getCourse().getId().equals(courseId)) {
            throw new IllegalArgumentException("Material does not belong to specified course");
        }

        if (material.getStatus() != MaterialStatus.READY) {
            throw new IllegalStateException("Video is not ready for playback. Current status: " + material.getStatus());
        }

        if (material.getAvailableFrom() != null && Instant.now().isBefore(material.getAvailableFrom())) {
            throw new AccessDeniedException("Material is not yet available");
        }

        boolean isMentor = material.getCourse().getMentorProfile().getUserId().equals(userId);
        if (!isMentor) {
            CourseEnrollment enrollment = enrollmentRepository.findByCourseIdAndStudentUserId(courseId, userId)
                    .orElseThrow(() -> new AccessDeniedException("User is not enrolled in this course"));
            validateEntitlementPolicy(enrollment, material);
        }

        // Generate Signed Token (5 minutes TTL)
        long expiresAt = Instant.now().plusSeconds(300).getEpochSecond();
        String signedPlaybackUrl = bunnyVideoClient.generateSignedPlaybackUrl(material.getBunnyVideoId(), 300);

        return CourseVideoPlaybackResponse.builder()
                .materialId(material.getId())
                .title(material.getTitle())
                .playbackUrl(signedPlaybackUrl)
                .thumbnailUrl(material.getThumbnailUrl())
                .durationSeconds(material.getDurationSeconds())
                .expiresAt(Instant.ofEpochSecond(expiresAt))
                .build();
    }

    private void validateEntitlementPolicy(CourseEnrollment enrollment, CourseMaterial material) {
        switch (enrollment.getStatus()) {
            case ACTIVE:
            case COMPLETED:
                return; // Full access
            case PARTIAL_REFUNDED:
                if (material.getAccessScope() == MaterialAccessScope.COURSE_LEVEL) {
                    return; // Public course materials allowed
                }
                if (material.getCourseSession() != null && material.getCourseSession().getStatus() == CourseSessionStatus.COMPLETED) {
                    return; // Completed sessions allowed
                }
                throw new AccessDeniedException("Partial refunded enrollment can only access completed session materials");
            case REFUNDED:
            case PENDING_PAYMENT:
            case PAYMENT_EXPIRED:
            case CANCELLED:
            default:
                throw new AccessDeniedException("No access entitlement for current enrollment status: " + enrollment.getStatus());
        }
    }

    @Override
    @Transactional
    public BunnyWebhookEvent saveWebhookAuditLog(String signature, String externalEventId, BunnyWebhookPayload payload) {
        // Idempotency check for webhook
        return webhookEventRepository.findByExternalEventId(externalEventId)
                .orElseGet(() -> {
                    // Optional: verify HMAC signature using bunnyProperties.getWebhookSecret()
                    BunnyWebhookEvent event = BunnyWebhookEvent.builder()
                            .externalEventId(externalEventId)
                            .eventType(String.valueOf(payload.getStatus())) // Bunny uses Status as event type mapping
                            .videoId(payload.getVideoGuid())
                            .libraryId(payload.getVideoLibraryId())
                            .payloadJson(payload.toString()) // or convert to json
                            .receivedAt(Instant.now())
                            .status("PENDING")
                            .build();
                    return webhookEventRepository.save(event);
                });
    }

    @Override
    @Transactional
    public void processWebhookEventIdempotent(BunnyWebhookEvent event) {
        if ("PROCESSED".equals(event.getStatus())) {
            return;
        }

        try {
            CourseMaterial material = materialRepository.findByBunnyLibraryIdAndBunnyVideoId(
                    event.getLibraryId(), event.getVideoId()).orElse(null);
            
            if (material != null) {
                int status = Integer.parseInt(event.getEventType());
                // Bunny status: 0=Created, 1=Uploading, 2=Processing, 3=Finished, 4=ResolutionFinished, 5=Failed, 6=PresignedUploadStarted
                if (status == 3 || status == 4) {
                    material.setStatus(MaterialStatus.READY);
                } else if (status == 5) {
                    material.setStatus(MaterialStatus.FAILED);
                } else if (status == 2) {
                    material.setStatus(MaterialStatus.PROCESSING);
                }
                materialRepository.save(material);
            }
            
            event.setStatus("PROCESSED");
            event.setProcessedAt(Instant.now());
        } catch (Exception e) {
            log.error("Failed to process webhook event {}", event.getId(), e);
            event.setStatus("FAILED");
            event.setLastError(e.getMessage());
            event.setRetryCount(event.getRetryCount() + 1);
        }
        webhookEventRepository.save(event);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<com.fptu.exe.skillswap.modules.course.dto.response.CourseMaterialSummaryResponse> getCourseMaterials(UUID userId, UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));

        boolean isMentor = course.getMentorProfile().getUserId().equals(userId);
        CourseEnrollment enrollment = isMentor ? null : enrollmentRepository.findByCourseIdAndStudentUserId(courseId, userId).orElse(null);
        
        java.util.List<CourseMaterial> materials = materialRepository.findByCourseIdOrderByUploadedAtAsc(courseId);
        
        return materials.stream().map(material -> {
            boolean isAvailable = false;
            String reason = null;
            
            if (isMentor) {
                isAvailable = true;
            } else if (enrollment == null) {
                reason = "NOT_ENROLLED";
            } else {
                try {
                    validateEntitlementPolicy(enrollment, material);
                    isAvailable = true;
                } catch (AccessDeniedException e) {
                    reason = "ENTITLEMENT_RESTRICTION";
                }
            }
            
            return com.fptu.exe.skillswap.modules.course.dto.response.CourseMaterialSummaryResponse.builder()
                    .materialId(material.getId())
                    .courseSessionId(material.getCourseSession() != null ? material.getCourseSession().getId() : null)
                    .title(material.getTitle())
                    .materialType(material.getMaterialType())
                    .storageProviderType(material.getStorageProviderType())
                    .status(material.getStatus())
                    .accessScope(material.getAccessScope())
                    .durationSeconds(material.getDurationSeconds())
                    .thumbnailUrl(material.getThumbnailUrl())
                    .uploadedAt(material.getUploadedAt())
                    .available(isAvailable)
                    .lockedReason(reason)
                    .build();
        }).collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteMaterial(UUID userId, UUID courseId, UUID materialId) {
        CourseMaterial material = materialRepository.findById(materialId)
                .orElseThrow(() -> new IllegalArgumentException("Material not found"));

        if (!material.getCourse().getId().equals(courseId)) {
            throw new IllegalArgumentException("Material does not belong to specified course");
        }

        if (!material.getCourse().getMentorProfile().getUserId().equals(userId)) {
            throw new AccessDeniedException("Only course mentor can delete materials");
        }

        // Soft delete and trigger outbox
        material.setStatus(MaterialStatus.DELETING);
        materialRepository.save(material);

        com.fptu.exe.skillswap.modules.course.domain.CourseOutboxEvent outboxEvent = com.fptu.exe.skillswap.modules.course.domain.CourseOutboxEvent.builder()
                .aggregateType("CourseMaterial")
                .aggregateId(material.getId())
                .eventType(com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxEventTypes.COURSE_MATERIAL_DELETE_REQUESTED)
                .payloadJson("{}")
                .status("PENDING")
                .build();
        // Since we are inside the same service, we should probably inject CourseOutboxEventRepository, or I can just autowire it.
        outboxEventRepository.save(outboxEvent);
    }
}
