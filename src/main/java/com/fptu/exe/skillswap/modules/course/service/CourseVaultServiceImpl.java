package com.fptu.exe.skillswap.modules.course.service;

import com.fptu.exe.skillswap.modules.course.domain.BunnyWebhookEvent;
import com.fptu.exe.skillswap.modules.course.domain.Course;
import com.fptu.exe.skillswap.modules.course.domain.CourseEnrollment;
import com.fptu.exe.skillswap.modules.course.domain.CourseOutboxEvent;
import com.fptu.exe.skillswap.modules.course.domain.CourseLecture;
import com.fptu.exe.skillswap.modules.course.domain.LectureResource;
import com.fptu.exe.skillswap.modules.course.domain.MaterialStatus;
import com.fptu.exe.skillswap.modules.course.domain.MaterialType;
import com.fptu.exe.skillswap.modules.course.domain.StorageProviderType;
import com.fptu.exe.skillswap.modules.course.dto.request.CreateVideoMaterialRequest;
import com.fptu.exe.skillswap.modules.course.dto.CourseVideoWebhook;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseMaterialSummaryResponse;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseVideoPlaybackResponse;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseVideoUploadInitResponse;
import com.fptu.exe.skillswap.modules.course.repository.BunnyWebhookEventRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseLectureRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseRepository;
import com.fptu.exe.skillswap.modules.course.repository.LectureResourceRepository;
import com.fptu.exe.skillswap.modules.course.port.CourseVideoProvider;
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

    private static final int BUNNY_STATUS_PROCESSING = 2;
    private static final int BUNNY_STATUS_FINISHED = 3;
    private static final int BUNNY_STATUS_RESOLUTION_FINISHED = 4;
    private static final int BUNNY_STATUS_FAILED = 5;

    private final CourseRepository courseRepository;
    private final CourseLectureRepository lectureRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final LectureResourceRepository resourceRepository;
    private final BunnyWebhookEventRepository webhookEventRepository;
    private final CourseVideoProvider courseVideoProvider;
    private final com.fptu.exe.skillswap.modules.course.repository.CourseOutboxEventRepository outboxEventRepository;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @Override
    public CourseVideoUploadInitResponse createVideoUpload(UUID mentorUserId, UUID courseId, CreateVideoMaterialRequest request) {
        UUID resourceId = createVideoUploadIntent(mentorUserId, courseId, request);
        return initializeVideoUpload(resourceId);
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

        CourseLecture lecture = lectureRepository.findById(request.getLectureId())
                .orElseThrow(() -> new BadRequestException(ErrorCode.BAD_REQUEST, "Lecture not found"));

        if (!lecture.getChapter().getCourse().getId().equals(courseId)) {
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "Lecture does not belong to specified course");
        }

        LectureResource resource = LectureResource.builder()
                .lecture(lecture)
                .title(request.getTitle())
                .resourceType(MaterialType.VIDEO)
                .storageProviderType(StorageProviderType.BUNNY_VIDEO)
                .status(MaterialStatus.UPLOADING_INTENT)
                .uploadedBy(mentorUserId)
                .uploadedAt(Instant.now())
                .build();

        resourceRepository.save(resource);
        outboxEventRepository.save(CourseOutboxEvent.builder()
                .aggregateType("LectureResource")
                .aggregateId(resource.getId())
                .eventType(com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxEventTypes.COURSE_MATERIAL_UPLOAD_INITIALIZATION_REQUESTED)
                .payloadJson("{}")
                .build());
        return resource.getId();
    }

    private CourseVideoUploadInitResponse initializeVideoUpload(UUID resourceId) {
        LectureResource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Lecture resource not found"));
        if (resource.getStatus() != MaterialStatus.UPLOADING_INTENT) {
            return toUploadResponse(resource);
        }
        CourseVideoProvider.CreatedVideo bunnyResponse = courseVideoProvider.createVideo(resource.getTitle());
        long expiresAt = Instant.now().plusSeconds(2 * 3600).getEpochSecond();
        String uploadSignature = courseVideoProvider.generateDirectUploadSignature(bunnyResponse.videoId(), expiresAt);
        try {
            return completeVideoUploadInitialization(resourceId, bunnyResponse, expiresAt, uploadSignature);
        } catch (RuntimeException completionFailure) {
            try {
                courseVideoProvider.deleteVideo(bunnyResponse.videoId());
            } catch (RuntimeException cleanupFailure) {
                log.error("Unable to compensate Bunny video {} after local initialization failure", bunnyResponse.videoId(), cleanupFailure);
            }
            throw completionFailure;
        }
    }

    private CourseVideoUploadInitResponse completeVideoUploadInitialization(UUID resourceId,
                                                                              CourseVideoProvider.CreatedVideo bunnyResponse,
                                                                              long expiresAt,
                                                                              String uploadSignature) {
        return transactionTemplate.execute(status -> {
            LectureResource resource = resourceRepository.findById(resourceId).orElseThrow();
            if (resource.getStatus() == MaterialStatus.UPLOADING_INTENT) {
                resource.setBunnyLibraryId(bunnyResponse.libraryId());
                resource.setBunnyVideoId(bunnyResponse.videoId());
                resource.setStatus(MaterialStatus.UPLOADING);
                resourceRepository.save(resource);
                outboxEventRepository.findActiveByAggregateIdAndEventType(resourceId,
                                com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxEventTypes.COURSE_MATERIAL_UPLOAD_INITIALIZATION_REQUESTED)
                        .forEach(event -> {
                            event.setStatus("PROCESSED");
                            event.setLastError(null);
                            event.setNextRetryAt(null);
                            event.setProcessingStartedAt(null);
                        });
            }
            return CourseVideoUploadInitResponse.builder()
                    .materialId(resource.getId())
                    .bunnyLibraryId(resource.getBunnyLibraryId())
                    .bunnyVideoId(resource.getBunnyVideoId())
                    .uploadUrl(String.format("https://video.bunnycdn.com/library/%s/videos/%s", resource.getBunnyLibraryId(), resource.getBunnyVideoId()))
                    .authorizationSignature(uploadSignature)
                    .expirationTimestamp(expiresAt)
                    .build();
        });
    }

    private CourseVideoUploadInitResponse toUploadResponse(LectureResource resource) {
        return CourseVideoUploadInitResponse.builder()
                .materialId(resource.getId())
                .bunnyLibraryId(resource.getBunnyLibraryId())
                .bunnyVideoId(resource.getBunnyVideoId())
                .uploadUrl(resource.getBunnyVideoId() == null ? null : String.format("https://video.bunnycdn.com/library/%s/videos/%s", resource.getBunnyLibraryId(), resource.getBunnyVideoId()))
                .expirationTimestamp(0)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CourseVideoPlaybackResponse getPlaybackAuthorization(UUID userId, UUID courseId, UUID materialId, String clientIp) {
        LectureResource resource = resourceRepository.findById(materialId)
                .orElseThrow(() -> new IllegalArgumentException("Resource not found"));

        Course course = resource.getLecture().getChapter().getCourse();
        if (!course.getId().equals(courseId)) {
            throw new IllegalArgumentException("Resource does not belong to specified course");
        }

        if (resource.getStatus() != MaterialStatus.READY) {
            throw new IllegalStateException("Video is not ready for playback. Current status: " + resource.getStatus());
        }

        boolean isMentor = course.getMentorProfile().getUserId().equals(userId);
        boolean isPreviewable = resource.getLecture().isPreviewable();

        if (!isMentor && !isPreviewable) {
            CourseEnrollment enrollment = enrollmentRepository.findByCourseIdAndStudentUserId(courseId, userId)
                    .orElseThrow(() -> new AccessDeniedException("User is not enrolled in this course"));
            validateEntitlementPolicy(enrollment);
        }

        long expiresAt = Instant.now().plusSeconds(60).getEpochSecond();
        String signedPlaybackUrl = courseVideoProvider.generateSignedPlaybackUrl(resource.getBunnyVideoId(), 60, clientIp);

        return CourseVideoPlaybackResponse.builder()
                .materialId(resource.getId())
                .title(resource.getTitle())
                .playbackUrl(signedPlaybackUrl)
                .thumbnailUrl(resource.getThumbnailUrl())
                .durationSeconds(resource.getDurationSeconds())
                .expiresAt(Instant.ofEpochSecond(expiresAt))
                .build();
    }

    private void validateEntitlementPolicy(CourseEnrollment enrollment) {
        switch (enrollment.getStatus()) {
            case ACTIVE:
            case COMPLETED:
                return;
            case REFUNDED:
            case PARTIAL_REFUNDED:
            case PENDING_PAYMENT:
            case PAYMENT_EXPIRED:
            case CANCELLED:
            default:
                throw new AccessDeniedException("No access entitlement for current enrollment status: " + enrollment.getStatus());
        }
    }

    @Override
    @Transactional
    public BunnyWebhookEvent saveWebhookAuditLog(String signature, String externalEventId, CourseVideoWebhook payload) {
        return webhookEventRepository.findByExternalEventId(externalEventId)
                .orElseGet(() -> {
                    BunnyWebhookEvent event = BunnyWebhookEvent.builder()
                            .externalEventId(externalEventId)
                            .eventType(String.valueOf(payload.status()))
                            .videoId(payload.videoId())
                            .libraryId(payload.libraryId())
                            .payloadJson(payload.toString())
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

        LectureResource resource = resourceRepository.findByBunnyVideoId(event.getVideoId()).orElse(null);
        if (resource != null) {
            applyWebhookStatus(resource, Integer.parseInt(event.getEventType()));
            resourceRepository.save(resource);
        }
        event.setStatus("PROCESSED");
        event.setProcessedAt(Instant.now());
        event.setProcessingStartedAt(null);
        event.setLastError(null);
    }

    private void applyWebhookStatus(LectureResource resource, int bunnyStatus) {
        if (resource.getStatus() == MaterialStatus.READY || resource.getStatus() == MaterialStatus.DELETING
                || resource.getStatus() == MaterialStatus.DELETED || resource.getStatus() == MaterialStatus.EXPIRED) {
            return;
        }
        if (bunnyStatus == BUNNY_STATUS_FINISHED || bunnyStatus == BUNNY_STATUS_RESOLUTION_FINISHED) {
            resource.setStatus(MaterialStatus.READY);
        } else if (bunnyStatus == BUNNY_STATUS_FAILED) {
            resource.setStatus(MaterialStatus.FAILED);
        } else if (bunnyStatus == BUNNY_STATUS_PROCESSING && resource.getStatus() == MaterialStatus.UPLOADING) {
            resource.setStatus(MaterialStatus.PROCESSING);
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

    private void processMaterialDeletion(UUID resourceId) {
        LectureResource resource = resourceRepository.findById(resourceId).orElseThrow();
        if (resource.getStatus() != MaterialStatus.DELETED && resource.getBunnyVideoId() != null && !resource.getBunnyVideoId().isBlank()) {
            courseVideoProvider.deleteVideo(resource.getBunnyVideoId());
        }
        transactionTemplate.executeWithoutResult(status -> {
            LectureResource locked = resourceRepository.findById(resourceId).orElseThrow();
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
    public List<CourseMaterialSummaryResponse> getCourseMaterials(UUID userId, UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));

        boolean isMentor = course.getMentorProfile().getUserId().equals(userId);
        CourseEnrollment enrollment = isMentor ? null : enrollmentRepository.findByCourseIdAndStudentUserId(courseId, userId).orElse(null);
        
        List<LectureResource> resources = resourceRepository.findAll().stream()
                .filter(r -> r.getLecture().getChapter().getCourse().getId().equals(courseId))
                .filter(r -> r.getStatus() != MaterialStatus.DELETED && r.getDeletedAt() == null)
                .toList();
        
        return resources.stream().map(resource -> {
            boolean isAvailable = false;
            String reason = null;
            
            if (isMentor || resource.getLecture().isPreviewable()) {
                isAvailable = true;
            } else if (enrollment == null) {
                reason = "NOT_ENROLLED";
            } else {
                try {
                    validateEntitlementPolicy(enrollment);
                    isAvailable = true;
                } catch (AccessDeniedException e) {
                    reason = "ENTITLEMENT_RESTRICTION";
                }
            }
            
            return CourseMaterialSummaryResponse.builder()
                    .resourceId(resource.getId())
                    .lectureId(resource.getLecture().getId())
                    .title(resource.getTitle())
                    .materialType(resource.getResourceType())
                    .storageProviderType(resource.getStorageProviderType())
                    .status(resource.getStatus())
                    .durationSeconds(resource.getDurationSeconds())
                    .thumbnailUrl(resource.getThumbnailUrl())
                    .uploadedAt(resource.getUploadedAt())
                    .available(isAvailable)
                    .lockedReason(reason)
                    .build();
        }).collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteMaterial(UUID userId, UUID courseId, UUID materialId) {
        LectureResource resource = resourceRepository.findById(materialId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));

        if (!resource.getLecture().getChapter().getCourse().getId().equals(courseId)) {
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "Resource does not belong to specified course");
        }

        if (!resource.getLecture().getChapter().getCourse().getMentorProfile().getUserId().equals(userId)) {
            throw new AccessDeniedException("Only course mentor can delete materials");
        }

        if (resource.getStatus() == MaterialStatus.DELETED || resource.getStatus() == MaterialStatus.DELETING) {
            return;
        }

        resource.setStatus(MaterialStatus.DELETING);
        resourceRepository.save(resource);

        outboxEventRepository.save(buildMaterialDeleteEvent(resource.getId()));
    }

    private com.fptu.exe.skillswap.modules.course.domain.CourseOutboxEvent buildMaterialDeleteEvent(UUID resourceId) {
        return com.fptu.exe.skillswap.modules.course.domain.CourseOutboxEvent.builder()
                .aggregateType("LectureResource")
                .aggregateId(resourceId)
                .eventType(com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxEventTypes.COURSE_MATERIAL_DELETE_REQUESTED)
                .payloadJson("{}")
                .status("PENDING")
                .build();
    }
}
