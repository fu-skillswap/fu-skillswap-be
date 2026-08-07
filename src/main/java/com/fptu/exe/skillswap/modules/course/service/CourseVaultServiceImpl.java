package com.fptu.exe.skillswap.modules.course.service;

import com.fptu.exe.skillswap.infrastructure.bunny.client.BunnyVideoClient;
import com.fptu.exe.skillswap.infrastructure.bunny.config.BunnyStreamProperties;
import com.fptu.exe.skillswap.infrastructure.bunny.dto.BunnyCreateVideoResponse;
import com.fptu.exe.skillswap.infrastructure.bunny.dto.BunnyWebhookPayload;
import com.fptu.exe.skillswap.modules.course.domain.BunnyWebhookEvent;
import com.fptu.exe.skillswap.modules.course.domain.Course;
import com.fptu.exe.skillswap.modules.course.domain.CourseEnrollment;
import com.fptu.exe.skillswap.modules.course.domain.CourseOutboxEvent;
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
import com.fptu.exe.skillswap.shared.exception.BadRequestException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseVaultServiceImpl implements CourseVaultService {

    // Bunny Stream video status codes as sent in the webhook payload
    private static final int BUNNY_STATUS_PROCESSING = 2;
    private static final int BUNNY_STATUS_FINISHED = 3;
    private static final int BUNNY_STATUS_RESOLUTION_FINISHED = 4;
    private static final int BUNNY_STATUS_FAILED = 5;

    private final CourseRepository courseRepository;
    private final CourseSessionRepository sessionRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final CourseMaterialRepository materialRepository;
    private final BunnyWebhookEventRepository webhookEventRepository;
    private final BunnyVideoClient bunnyVideoClient;
    private final BunnyStreamProperties bunnyProperties;
    private final com.fptu.exe.skillswap.modules.course.repository.CourseOutboxEventRepository outboxEventRepository;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @Override
    public CourseVideoUploadInitResponse createVideoUpload(UUID mentorUserId, UUID courseId, CreateVideoMaterialRequest request) {
        UUID materialId = createVideoUploadIntent(mentorUserId, courseId, request);
        // The intent and recovery event are committed before this network call.
        return initializeVideoUpload(materialId);
    }

    private UUID createVideoUploadIntent(UUID mentorUserId, UUID courseId, CreateVideoMaterialRequest request) {
        return transactionTemplate.execute(status -> createVideoUploadIntentInTransaction(mentorUserId, courseId, request));
    }

    @Transactional
    protected UUID createVideoUploadIntentInTransaction(UUID mentorUserId, UUID courseId, CreateVideoMaterialRequest request) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));

        if (!course.getMentorProfile().getUserId().equals(mentorUserId)) {
            throw new AccessDeniedException("Only course mentor can upload materials");
        }

        var courseSession = request.getCourseSessionId() == null ? null : sessionRepository
                .findByIdAndCourseId(request.getCourseSessionId(), courseId)
                .orElseThrow(() -> new BadRequestException(ErrorCode.BAD_REQUEST,
                        "Course session does not belong to specified course"));

        CourseMaterial material = CourseMaterial.builder()
                .course(course)
                .courseSession(courseSession)
                .title(request.getTitle())
                .materialType(MaterialType.VIDEO)
                .storageProviderType(StorageProviderType.BUNNY_VIDEO)
                .status(MaterialStatus.UPLOADING_INTENT)
                .accessScope(request.getCourseSessionId() != null ? MaterialAccessScope.SESSION_LEVEL : MaterialAccessScope.COURSE_LEVEL)
                .uploadedBy(mentorUserId)
                .uploadedAt(Instant.now())
                .build();

        materialRepository.save(material);
        outboxEventRepository.save(CourseOutboxEvent.builder()
                .aggregateType("CourseMaterial")
                .aggregateId(material.getId())
                .eventType(com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxEventTypes.COURSE_MATERIAL_UPLOAD_INITIALIZATION_REQUESTED)
                .payloadJson("{}")
                .build());
        return material.getId();
    }

    private CourseVideoUploadInitResponse initializeVideoUpload(UUID materialId) {
        CourseMaterial material = materialRepository.findById(materialId)
                .orElseThrow(() -> new ResourceNotFoundException("Course material not found"));
        if (material.getStatus() != MaterialStatus.UPLOADING_INTENT) {
            return toUploadResponse(material);
        }
        BunnyCreateVideoResponse bunnyResponse = bunnyVideoClient.createVideo(material.getTitle());
        long expiresAt = Instant.now().plusSeconds(2 * 3600).getEpochSecond();
        String uploadSignature = bunnyVideoClient.generateDirectUploadSignature(bunnyResponse.getGuid(), expiresAt);
        try {
            return completeVideoUploadInitialization(materialId, bunnyResponse, expiresAt, uploadSignature);
        } catch (RuntimeException completionFailure) {
            // Bunny has no create idempotency key. Compensate a known remote object when local persistence fails.
            try {
                bunnyVideoClient.deleteVideo(bunnyResponse.getGuid());
            } catch (RuntimeException cleanupFailure) {
                log.error("Unable to compensate Bunny video {} after local initialization failure", bunnyResponse.getGuid(), cleanupFailure);
            }
            throw completionFailure;
        }
    }

    private CourseVideoUploadInitResponse completeVideoUploadInitialization(UUID materialId,
                                                                              BunnyCreateVideoResponse bunnyResponse,
                                                                              long expiresAt,
                                                                              String uploadSignature) {
        return transactionTemplate.execute(status -> {
                    CourseMaterial material = materialRepository.findById(materialId).orElseThrow();
                    if (material.getStatus() == MaterialStatus.UPLOADING_INTENT) {
                        material.setBunnyLibraryId(bunnyProperties.getLibraryId());
                        material.setBunnyVideoId(bunnyResponse.getGuid());
                        material.setStatus(MaterialStatus.UPLOADING);
                        materialRepository.save(material);
                        outboxEventRepository.findActiveByAggregateIdAndEventType(materialId,
                                        com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxEventTypes.COURSE_MATERIAL_UPLOAD_INITIALIZATION_REQUESTED)
                                .forEach(event -> {
                                    event.setStatus("PROCESSED");
                                    event.setLastError(null);
                                    event.setNextRetryAt(null);
                                    event.setProcessingStartedAt(null);
                                });
                    }
                    return CourseVideoUploadInitResponse.builder()
                            .materialId(material.getId())
                            .bunnyLibraryId(material.getBunnyLibraryId())
                            .bunnyVideoId(material.getBunnyVideoId())
                            .uploadUrl(String.format("https://video.bunnycdn.com/library/%s/videos/%s", material.getBunnyLibraryId(), material.getBunnyVideoId()))
                            .authorizationSignature(uploadSignature)
                            .expirationTimestamp(expiresAt)
                            .build();
                });
    }

    private CourseVideoUploadInitResponse toUploadResponse(CourseMaterial material) {
        return CourseVideoUploadInitResponse.builder()
                .materialId(material.getId())
                .bunnyLibraryId(material.getBunnyLibraryId())
                .bunnyVideoId(material.getBunnyVideoId())
                .uploadUrl(material.getBunnyVideoId() == null ? null : String.format("https://video.bunnycdn.com/library/%s/videos/%s", material.getBunnyLibraryId(), material.getBunnyVideoId()))
                .expirationTimestamp(0)
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
    public List<UUID> claimWebhookEvents(int limit) {
        Instant now = Instant.now();
        List<UUID> ids = webhookEventRepository.findClaimableIdsForUpdateSkipLocked(now, now.minus(5, ChronoUnit.MINUTES), limit);
        for (UUID id : ids) {
            BunnyWebhookEvent event = webhookEventRepository.findByIdForUpdate(id).orElseThrow();
            event.setStatus("PROCESSING");
            event.setProcessingStartedAt(now);
            event.setLastError(null);
        }
        return ids;
    }

    @Override
    @Transactional
    public void processWebhookEventIdempotent(UUID eventId) {
        BunnyWebhookEvent event = webhookEventRepository.findByIdForUpdate(eventId).orElseThrow();
        if ("PROCESSED".equals(event.getStatus()) || "DEAD_LETTER".equals(event.getStatus())) {
            return;
        }
        if (!"PROCESSING".equals(event.getStatus())) {
            throw new IllegalStateException("Webhook event must be claimed before processing");
        }

        CourseMaterial material = materialRepository.findByBunnyLibraryIdAndBunnyVideoId(
                event.getLibraryId(), event.getVideoId()).orElse(null);
        if (material != null) {
            applyWebhookStatus(material, Integer.parseInt(event.getEventType()));
            materialRepository.save(material);
        }
        event.setStatus("PROCESSED");
        event.setProcessedAt(Instant.now());
        event.setProcessingStartedAt(null);
        event.setLastError(null);
    }

    private void applyWebhookStatus(CourseMaterial material, int bunnyStatus) {
        // Provider delivery is at-least-once and may be out of order. READY is terminal for upload processing.
        if (material.getStatus() == MaterialStatus.READY || material.getStatus() == MaterialStatus.DELETING
                || material.getStatus() == MaterialStatus.DELETED || material.getStatus() == MaterialStatus.EXPIRED) {
            return;
        }
        if (bunnyStatus == BUNNY_STATUS_FINISHED || bunnyStatus == BUNNY_STATUS_RESOLUTION_FINISHED) {
            material.setStatus(MaterialStatus.READY);
        } else if (bunnyStatus == BUNNY_STATUS_FAILED) {
            material.setStatus(MaterialStatus.FAILED);
        } else if (bunnyStatus == BUNNY_STATUS_PROCESSING && material.getStatus() == MaterialStatus.UPLOADING) {
            material.setStatus(MaterialStatus.PROCESSING);
        }
    }

    @Override
    @Transactional
    public void markWebhookEventFailed(UUID eventId, Throwable cause) {
        BunnyWebhookEvent event = webhookEventRepository.findByIdForUpdate(eventId).orElseThrow();
        if ("PROCESSED".equals(event.getStatus()) || "DEAD_LETTER".equals(event.getStatus())) {
            return;
        }
        int retryCount = event.getRetryCount() + 1;
        event.setRetryCount(retryCount);
        event.setProcessingStartedAt(null);
        event.setLastError(truncate(cause.getMessage()));
        if (retryCount >= 5) {
            event.setStatus("DEAD_LETTER");
        } else {
            event.setStatus("FAILED");
            event.setNextRetryAt(nextRetryAt(retryCount));
        }
    }

    @Override
    @Transactional
    public List<UUID> claimCourseOutboxEvents(int limit) {
        Instant now = Instant.now();
        List<UUID> ids = outboxEventRepository.findClaimableIdsForUpdateSkipLocked(now, now.minus(5, ChronoUnit.MINUTES), limit);
        for (UUID id : ids) {
            CourseOutboxEvent event = outboxEventRepository.findByIdForUpdate(id).orElseThrow();
            event.setStatus("PROCESSING");
            event.setProcessingStartedAt(now);
            event.setLastError(null);
        }
        return ids;
    }

    @Override
    public void processCourseOutboxEvent(UUID eventId) {
        CourseOutboxEvent event = outboxEventRepository.findById(eventId).orElseThrow();
        if (!"PROCESSING".equals(event.getStatus())) {
            return;
        }
        if (com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxEventTypes.COURSE_MATERIAL_UPLOAD_INITIALIZATION_REQUESTED
                .equals(event.getEventType())) {
            initializeVideoUpload(event.getAggregateId());
            return;
        }
        if (com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxEventTypes.COURSE_MATERIAL_DELETE_REQUESTED
                .equals(event.getEventType())) {
            processMaterialDeletion(event.getAggregateId());
            markOutboxProcessed(eventId);
            return;
        }
        markOutboxProcessed(eventId);
    }

    private void processMaterialDeletion(UUID materialId) {
        CourseMaterial material = materialRepository.findById(materialId).orElseThrow();
        if (material.getStatus() != MaterialStatus.DELETED && material.getBunnyVideoId() != null && !material.getBunnyVideoId().isBlank()) {
            bunnyVideoClient.deleteVideo(material.getBunnyVideoId());
        }
        transactionTemplate.executeWithoutResult(status -> {
            CourseMaterial locked = materialRepository.findById(materialId).orElseThrow();
            if (locked.getStatus() != MaterialStatus.DELETED) {
                locked.setStatus(MaterialStatus.DELETED);
                locked.setDeletedAt(Instant.now());
            }
        });
    }

    private void markOutboxProcessed(UUID eventId) {
        transactionTemplate.executeWithoutResult(status -> {
            CourseOutboxEvent event = outboxEventRepository.findByIdForUpdate(eventId).orElseThrow();
            event.setStatus("PROCESSED");
            event.setProcessingStartedAt(null);
            event.setNextRetryAt(null);
            event.setLastError(null);
        });
    }

    @Override
    @Transactional
    public void markCourseOutboxEventFailed(UUID eventId, Throwable cause) {
        CourseOutboxEvent event = outboxEventRepository.findByIdForUpdate(eventId).orElseThrow();
        if ("PROCESSED".equals(event.getStatus()) || "DEAD_LETTER".equals(event.getStatus())) {
            return;
        }
        int retryCount = event.getRetryCount() + 1;
        event.setRetryCount(retryCount);
        event.setProcessingStartedAt(null);
        event.setLastError(truncate(cause.getMessage()));
        if (retryCount >= 5) {
            event.setStatus("DEAD_LETTER");
        } else {
            event.setStatus("FAILED");
            event.setNextRetryAt(nextRetryAt(retryCount));
        }
    }

    private Instant nextRetryAt(int retryCount) {
        long[] seconds = {10L, 30L, 60L, 300L};
        return Instant.now().plus(seconds[Math.min(Math.max(retryCount - 1, 0), seconds.length - 1)], ChronoUnit.SECONDS);
    }

    private String truncate(String message) {
        return message == null ? "Unknown failure" : message.substring(0, Math.min(message.length(), 500));
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<com.fptu.exe.skillswap.modules.course.dto.response.CourseMaterialSummaryResponse> getCourseMaterials(UUID userId, UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));

        boolean isMentor = course.getMentorProfile().getUserId().equals(userId);
        CourseEnrollment enrollment = isMentor ? null : enrollmentRepository.findByCourseIdAndStudentUserId(courseId, userId).orElse(null);
        
        java.util.List<CourseMaterial> materials = materialRepository
                .findByCourseIdAndStatusNotAndDeletedAtIsNullOrderByUploadedAtAsc(courseId, MaterialStatus.DELETED);
        
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
                .orElseThrow(() -> new ResourceNotFoundException("Material not found"));

        if (!material.getCourse().getId().equals(courseId)) {
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "Material does not belong to specified course");
        }

        if (!material.getCourse().getMentorProfile().getUserId().equals(userId)) {
            throw new AccessDeniedException("Only course mentor can delete materials");
        }

        if (material.getStatus() == MaterialStatus.DELETED || material.getStatus() == MaterialStatus.DELETING) {
            return;
        }

        material.setStatus(MaterialStatus.DELETING);
        material.setDeleteRequestedAt(Instant.now());
        materialRepository.save(material);

        outboxEventRepository.save(buildMaterialDeleteEvent(material.getId()));
    }

    private com.fptu.exe.skillswap.modules.course.domain.CourseOutboxEvent buildMaterialDeleteEvent(UUID materialId) {
        return com.fptu.exe.skillswap.modules.course.domain.CourseOutboxEvent.builder()
                .aggregateType("CourseMaterial")
                .aggregateId(materialId)
                .eventType(com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxEventTypes.COURSE_MATERIAL_DELETE_REQUESTED)
                .payloadJson("{}")
                .status("PENDING")
                .build();
    }
}
