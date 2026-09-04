package com.fptu.exe.skillswap.modules.course.service;

import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.infrastructure.config.CourseMaterialProperties;
import com.fptu.exe.skillswap.modules.course.domain.*;
import com.fptu.exe.skillswap.modules.course.dto.CourseVideoWebhook;
import com.fptu.exe.skillswap.modules.course.dto.request.CreatePdfMaterialUploadRequest;
import com.fptu.exe.skillswap.modules.course.dto.request.CreateVideoMaterialRequest;
import com.fptu.exe.skillswap.modules.course.dto.response.*;
import com.fptu.exe.skillswap.modules.course.port.CourseVideoProvider;
import com.fptu.exe.skillswap.modules.course.repository.*;
import com.fptu.exe.skillswap.modules.mentor.port.MentorOwnershipQueryPort;
import com.fptu.exe.skillswap.shared.exception.BadRequestException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxEventTypes;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import com.fptu.exe.skillswap.shared.dto.response.ProviderNeutralUploadMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** Media lifecycle for the Course -> Chapter -> Material curriculum. */
@Service
@RequiredArgsConstructor
@Slf4j
public class CourseVaultServiceImpl implements CourseVaultService {
    private static final int BUNNY_STATUS_PROCESSING = 2, BUNNY_STATUS_FINISHED = 3,
            BUNNY_STATUS_RESOLUTION_FINISHED = 4, BUNNY_STATUS_FAILED = 5;

    private final CourseRepository courseRepository;
    private final CourseChapterRepository chapterRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final CourseMaterialRepository materialRepository;
    private final BunnyWebhookEventRepository webhookEventRepository;
    private final CourseOutboxEventRepository outboxEventRepository;
    private final CourseVideoProvider courseVideoProvider;
    private final StorageGateway storageGateway;
    private final CourseMaterialProperties materialProperties;
    private final TransactionTemplate transactionTemplate;
    private final MentorOwnershipQueryPort mentorOwnershipQueryPort;
    private final CourseAnnouncementNotificationService courseAnnouncementNotificationService;
    private final TimeProvider timeProvider;

    @Override
    public CourseVideoUploadInitResponse createVideoUpload(UUID mentorUserId, UUID courseId, UUID chapterId, CreateVideoMaterialRequest request) {
        UUID materialId = transactionTemplate.execute(s -> createVideoIntent(mentorUserId, courseId, chapterId, request));
        return initializeVideoUpload(materialId);
    }

    @Transactional
    protected UUID createVideoIntent(UUID userId, UUID courseId, UUID chapterId, CreateVideoMaterialRequest request) {
        CourseChapter chapter = ownedChapter(userId, courseId, chapterId);
        assertUnusedOrder(chapterId, request.getSortOrder(), null);
        CourseMaterial material = materialRepository.save(CourseMaterial.builder().chapter(chapter).title(request.getTitle())
                .materialType(CourseMaterialType.VIDEO).sortOrder(request.getSortOrder())
                .isPreviewable(Boolean.TRUE.equals(request.getPreviewable())).isPublished(!Boolean.FALSE.equals(request.getPublished()))
                .storageProviderType(StorageProviderType.BUNNY_VIDEO).status(MaterialStatus.UPLOADING_INTENT)
                .uploadedBy(userId).uploadedAt(timeProvider.instant()).build());
        outboxEventRepository.save(outbox(material.getId(), DomainEventOutboxEventTypes.COURSE_MATERIAL_UPLOAD_INITIALIZATION_REQUESTED));
        return material.getId();
    }

    private CourseVideoUploadInitResponse initializeVideoUpload(UUID materialId) {
        CourseMaterial material = materialRepository.findById(materialId).orElseThrow(() -> new ResourceNotFoundException("Course material not found"));
        if (material.getStatus() != MaterialStatus.UPLOADING_INTENT) return uploadResponse(material);
        CourseVideoProvider.CreatedVideo created = courseVideoProvider.createVideo(material.getTitle());
        long expiry = timeProvider.instant().plus(Duration.ofHours(2)).getEpochSecond();
        try {
            return transactionTemplate.execute(s -> {
                CourseMaterial locked = materialRepository.findById(materialId).orElseThrow();
                if (locked.getStatus() == MaterialStatus.UPLOADING_INTENT) {
                    locked.setBunnyLibraryId(created.libraryId()); locked.setBunnyVideoId(created.videoId()); locked.setStatus(MaterialStatus.UPLOADING);
                    outboxEventRepository.findActiveByAggregateIdAndEventType(materialId, DomainEventOutboxEventTypes.COURSE_MATERIAL_UPLOAD_INITIALIZATION_REQUESTED)
                            .forEach(e -> { e.setStatus("PROCESSED"); e.setProcessingStartedAt(null); e.setNextRetryAt(null); });
                }
                return videoUploadResponse(locked, expiry, courseVideoProvider.generateDirectUploadSignature(created.videoId(), expiry));
            });
        } catch (RuntimeException error) {
            try { courseVideoProvider.deleteVideo(created.videoId()); } catch (RuntimeException cleanup) { log.warn("Could not delete provisional Bunny video", cleanup); }
            throw error;
        }
    }

    @Override
    @Transactional
    public CoursePdfUploadInitResponse createPdfUpload(UUID userId, UUID courseId, UUID chapterId, CreatePdfMaterialUploadRequest request) {
        if (!request.filename().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) throw new BadRequestException(ErrorCode.BAD_REQUEST, "Only PDF files are supported");
        CourseChapter chapter = ownedChapter(userId, courseId, chapterId);
        assertUnusedOrder(chapterId, request.sortOrder(), null);
        CourseMaterial material = materialRepository.save(CourseMaterial.builder().chapter(chapter).title(request.title()).materialType(CourseMaterialType.PDF)
                .sortOrder(request.sortOrder()).isPreviewable(Boolean.TRUE.equals(request.previewable())).isPublished(!Boolean.FALSE.equals(request.published()))
                .storageProviderType(StorageProviderType.OBJECT_STORAGE).status(MaterialStatus.UPLOADING).uploadedBy(userId).uploadedAt(timeProvider.instant())
                .uploadExpiresAt(timeProvider.instant().plus(pdfUploadTtl())).build());
        String key = "course-materials/" + userId + "/" + material.getId() + "/upload.pdf";
        material.setDocumentObjectKey(key);
        StorageGateway.PrivatePresignedUpload upload = storageGateway.generatePrivateUploadUrl(key, "application/pdf", pdfUploadTtl());
        return new CoursePdfUploadInitResponse(material.getId(), upload.uploadUrl(), upload.expiresAt(), "application/pdf",
                new ProviderNeutralUploadMetadata(material.getId(), null, upload.uploadUrl(), upload.expiresAt(),
                        "COURSE_PDF", java.util.Map.of("Content-Type", "application/pdf")));
    }

    @Override
    @Transactional
    public void confirmPdfUpload(UUID userId, UUID courseId, UUID materialId, String objectKey) {
        CourseMaterial material = ownedMaterial(userId, courseId, materialId);
        if (material.getMaterialType() != CourseMaterialType.PDF || material.getStatus() != MaterialStatus.UPLOADING || !objectKey.equals(material.getDocumentObjectKey()))
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "PDF upload cannot be confirmed");
        StorageGateway.ObjectMetadata object = storageGateway.headObject(objectKey);
        if (!"application/pdf".equalsIgnoreCase(object.contentType()) || object.sizeBytes() <= 0 || object.sizeBytes() > maxPdfBytes())
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "Uploaded file must be a PDF within the configured size limit");
        material.setFileSizeBytes(object.sizeBytes()); material.setStatus(MaterialStatus.READY); material.setUploadExpiresAt(null);
        refreshCourseTotals(material.getChapter().getCourse());
    }

    @Override
    @Transactional(readOnly = true)
    public CourseVideoPlaybackResponse getPlaybackAuthorization(UUID userId, UUID courseId, UUID materialId, String clientIp) {
        CourseMaterial material = materialForCourse(courseId, materialId);
        if (material.getMaterialType() != CourseMaterialType.VIDEO) throw new BadRequestException(ErrorCode.BAD_REQUEST, "Material is not a video");
        assertAvailable(userId, material);
        if (material.getStatus() != MaterialStatus.READY) throw new BadRequestException(ErrorCode.BAD_REQUEST, "Video is not ready for playback");
        long expires = timeProvider.instant().plusSeconds(60).getEpochSecond();
        return CourseVideoPlaybackResponse.builder().materialId(material.getId()).title(material.getTitle())
                .playbackUrl(courseVideoProvider.generateSignedPlaybackUrl(material.getBunnyVideoId(), 60, clientIp))
                .thumbnailUrl(material.getThumbnailUrl()).durationSeconds(material.getDurationSeconds()).expiresAt(Instant.ofEpochSecond(expires)).build();
    }

    @Override
    @Transactional(readOnly = true)
    public CourseMaterialDownloadResponse getPdfDownload(UUID userId, UUID courseId, UUID materialId) {
        CourseMaterial material = materialForCourse(courseId, materialId);
        if (material.getMaterialType() != CourseMaterialType.PDF || material.getStatus() != MaterialStatus.READY) throw new BadRequestException(ErrorCode.BAD_REQUEST, "PDF is not ready");
        assertAvailable(userId, material);
        StorageGateway.PrivatePresignedDownload download = storageGateway.generatePrivateDownloadUrl(material.getDocumentObjectKey(), pdfDownloadTtl(),
                "attachment; filename=\"" + material.getTitle().replace("\"", "") + ".pdf\"");
        return new CourseMaterialDownloadResponse(download.downloadUrl(), download.expiresAt());
    }

    @Override @Transactional
    public BunnyWebhookEvent saveWebhookAuditLog(String signature, String externalId, CourseVideoWebhook payload) {
        return webhookEventRepository.findByExternalEventId(externalId).orElseGet(() -> webhookEventRepository.save(BunnyWebhookEvent.builder()
                .externalEventId(externalId).eventType(String.valueOf(payload.status())).videoId(payload.videoId()).libraryId(payload.libraryId())
                .payloadJson(payload.toString()).receivedAt(timeProvider.instant()).status("PENDING").build()));
    }
    @Override @Transactional public List<UUID> claimWebhookEvents(int limit) { return claimWebhookEventsInternal(limit); }
    private List<UUID> claimWebhookEventsInternal(int limit) { Instant now=timeProvider.instant(); List<UUID> ids=webhookEventRepository.findClaimableIdsForUpdateSkipLocked(now,now.minus(5,ChronoUnit.MINUTES),limit); ids.forEach(id->{BunnyWebhookEvent e=webhookEventRepository.findByIdForUpdate(id).orElseThrow();e.setStatus("PROCESSING");e.setProcessingStartedAt(now);e.setLastError(null);});return ids; }
    @Override @Transactional public void processWebhookEventIdempotent(UUID id) { BunnyWebhookEvent e=webhookEventRepository.findByIdForUpdate(id).orElseThrow(); if (!"PROCESSING".equals(e.getStatus())) return; CourseMaterial m=materialRepository.findByBunnyVideoId(e.getVideoId()).orElse(null); if(m!=null){applyWebhookStatus(m,Integer.parseInt(e.getEventType()));refreshCourseTotals(m.getChapter().getCourse());} e.setStatus("PROCESSED");e.setProcessedAt(timeProvider.instant());e.setProcessingStartedAt(null);e.setLastError(null); }
    private void applyWebhookStatus(CourseMaterial m,int status) { if(m.getStatus()==MaterialStatus.READY||m.getStatus()==MaterialStatus.DELETING||m.getStatus()==MaterialStatus.DELETED||m.getStatus()==MaterialStatus.EXPIRED)return; if(status==BUNNY_STATUS_FINISHED||status==BUNNY_STATUS_RESOLUTION_FINISHED)m.setStatus(MaterialStatus.READY);else if(status==BUNNY_STATUS_FAILED)m.setStatus(MaterialStatus.FAILED);else if(status==BUNNY_STATUS_PROCESSING&&m.getStatus()==MaterialStatus.UPLOADING)m.setStatus(MaterialStatus.PROCESSING); }
    @Override @Transactional public void markWebhookEventFailed(UUID id, Throwable cause) { BunnyWebhookEvent e=webhookEventRepository.findByIdForUpdate(id).orElseThrow();fail(e, cause); }
    @Override @Transactional public List<UUID> claimCourseOutboxEvents(int limit) { Instant now=timeProvider.instant(); List<UUID> ids=outboxEventRepository.findClaimableIdsForUpdateSkipLocked(now,now.minus(5,ChronoUnit.MINUTES),limit);ids.forEach(id->{CourseOutboxEvent e=outboxEventRepository.findByIdForUpdate(id).orElseThrow();e.setStatus("PROCESSING");e.setProcessingStartedAt(now);e.setLastError(null);});return ids; }
    @Override public void processCourseOutboxEvent(UUID id) { CourseOutboxEvent e=outboxEventRepository.findById(id).orElseThrow();if(!"PROCESSING".equals(e.getStatus()))return;if(DomainEventOutboxEventTypes.COURSE_MATERIAL_UPLOAD_INITIALIZATION_REQUESTED.equals(e.getEventType())){initializeVideoUpload(e.getAggregateId());return;}if(DomainEventOutboxEventTypes.COURSE_MATERIAL_DELETE_REQUESTED.equals(e.getEventType())){processMaterialDeletion(e.getAggregateId());markOutboxProcessed(id);return;}if(DomainEventOutboxEventTypes.COURSE_ANNOUNCEMENT_CREATED.equals(e.getEventType())){courseAnnouncementNotificationService.process(e.getAggregateId());}markOutboxProcessed(id); }
    private void processMaterialDeletion(UUID id) { CourseMaterial m=materialRepository.findById(id).orElseThrow();if(m.getStorageProviderType()==StorageProviderType.BUNNY_VIDEO&&m.getBunnyVideoId()!=null)courseVideoProvider.deleteVideo(m.getBunnyVideoId());if(m.getStorageProviderType()==StorageProviderType.OBJECT_STORAGE&&m.getDocumentObjectKey()!=null)storageGateway.deletePrivateObject(m.getDocumentObjectKey());transactionTemplate.executeWithoutResult(s->{CourseMaterial locked=materialRepository.findById(id).orElseThrow();locked.setStatus(MaterialStatus.DELETED);locked.setDeletedAt(timeProvider.instant());refreshCourseTotals(locked.getChapter().getCourse());}); }
    private void markOutboxProcessed(UUID id){transactionTemplate.executeWithoutResult(s->{CourseOutboxEvent e=outboxEventRepository.findByIdForUpdate(id).orElseThrow();e.setStatus("PROCESSED");e.setProcessingStartedAt(null);e.setNextRetryAt(null);e.setLastError(null);});}
    @Override @Transactional public void markCourseOutboxEventFailed(UUID id,Throwable cause){fail(outboxEventRepository.findByIdForUpdate(id).orElseThrow(),cause);}
    private void fail(BunnyWebhookEvent e,Throwable cause){int n=e.getRetryCount()+1;e.setRetryCount(n);e.setProcessingStartedAt(null);e.setLastError(truncate(cause.getMessage()));if(n>=5)e.setStatus("DEAD_LETTER");else{e.setStatus("FAILED");e.setNextRetryAt(nextRetry(n));}}
    private void fail(CourseOutboxEvent e,Throwable cause){int n=e.getRetryCount()+1;e.setRetryCount(n);e.setProcessingStartedAt(null);e.setLastError(truncate(cause.getMessage()));if(n>=5)e.setStatus("DEAD_LETTER");else{e.setStatus("FAILED");e.setNextRetryAt(nextRetry(n));}}
    private Instant nextRetry(int n){long[] s={10,30,60,300};return timeProvider.instant().plusSeconds(s[Math.min(n-1,s.length-1)]);} private String truncate(String s){return s==null?"Unknown failure":s.substring(0,Math.min(s.length(),500));}

    @Override @Transactional(readOnly=true) public List<CourseMaterialSummaryResponse> getCourseMaterials(UUID userId, UUID courseId) { return materialRepository.findActiveByCourseIdOrderByCurriculum(courseId).stream().map(m -> { boolean available = canAccess(userId, m); return CourseMaterialSummaryResponse.builder().materialId(m.getId()).chapterId(m.getChapter().getId()).title(m.getTitle()).materialType(m.getMaterialType()).storageProviderType(m.getStorageProviderType()).status(m.getStatus()).durationSeconds(m.getDurationSeconds()).thumbnailUrl(m.getThumbnailUrl()).uploadedAt(m.getUploadedAt()).available(available).lockedReason(available?null:"NOT_ENROLLED").userActionMessage(CourseMaterialSummaryResponse.userActionMessage(available, m.getStatus())).retryable(CourseMaterialSummaryResponse.retryable(available, m.getStatus())).build(); }).toList(); }
    @Override @Transactional public void deleteMaterial(UUID userId,UUID courseId,UUID materialId){CourseMaterial m=ownedMaterial(userId,courseId,materialId);if(m.getStatus()==MaterialStatus.DELETED||m.getStatus()==MaterialStatus.DELETING)return;m.setStatus(MaterialStatus.DELETING);outboxEventRepository.save(outbox(m.getId(),DomainEventOutboxEventTypes.COURSE_MATERIAL_DELETE_REQUESTED));}

    private CourseChapter ownedChapter(UUID userId,UUID courseId,UUID chapterId){CourseChapter c=chapterRepository.findById(chapterId).orElseThrow(()->new ResourceNotFoundException("Chapter not found"));if(!c.getCourse().getId().equals(courseId))throw new BadRequestException(ErrorCode.BAD_REQUEST,"Chapter does not belong to course");if(!isCourseMentor(userId, courseId))throw new AccessDeniedException("Only course mentor can change curriculum");return c;}
    private CourseMaterial ownedMaterial(UUID userId,UUID courseId,UUID materialId){CourseMaterial m=materialForCourse(courseId,materialId);if(!isCourseMentor(userId, courseId))throw new AccessDeniedException("Only course mentor can change curriculum");return m;}
    private CourseMaterial materialForCourse(UUID courseId,UUID materialId){CourseMaterial m=materialRepository.findActiveWithCurriculumById(materialId).orElseThrow(()->new ResourceNotFoundException("Course material not found"));if(!m.getChapter().getCourse().getId().equals(courseId))throw new BadRequestException(ErrorCode.BAD_REQUEST,"Material does not belong to course");return m;}
    private void assertUnusedOrder(UUID chapterId,int order,UUID self){materialRepository.findByChapterIdAndDeletedAtIsNullOrderBySortOrderAsc(chapterId).stream().filter(m->m.getSortOrder()==order&&!m.getId().equals(self)).findAny().ifPresent(m->{throw new BadRequestException(ErrorCode.RESOURCE_CONFLICT,"Material sort order already exists in this chapter");});}
    private boolean canAccess(UUID user,CourseMaterial m){try{assertAvailable(user,m);return true;}catch(AccessDeniedException e){return false;}}
    private void assertAvailable(UUID user,CourseMaterial m){Course c=m.getChapter().getCourse();if(isCourseMentor(user, c.getId())||m.isPreviewable())return;CourseEnrollment e=enrollmentRepository.findByCourseIdAndStudentUserId(c.getId(),user).orElseThrow(()->new AccessDeniedException("User is not enrolled"));if(e.getStatus()!=EnrollmentStatus.ACTIVE&&e.getStatus()!=EnrollmentStatus.COMPLETED)throw new AccessDeniedException("No active course entitlement");}
    private boolean isCourseMentor(UUID userId, UUID courseId) {
        return courseRepository.findMentorUserIdByCourseId(courseId)
                .map(mentorUserId -> mentorOwnershipQueryPort.isOwnedBy(mentorUserId, userId))
                .orElse(false);
    }
    private CourseOutboxEvent outbox(UUID id,String type){return CourseOutboxEvent.builder().aggregateType("CourseMaterial").aggregateId(id).eventType(type).payloadJson("{}").status("PENDING").build();}
    private void refreshCourseTotals(Course c){c.setTotalMaterials(Math.toIntExact(materialRepository.countByChapterCourseIdAndDeletedAtIsNullAndIsPublishedTrue(c.getId())));}
    private Duration pdfUploadTtl() { return Duration.ofMinutes(materialProperties.getPdfUploadTtlMinutes()); }
    private Duration pdfDownloadTtl() { return Duration.ofMinutes(materialProperties.getPdfDownloadTtlMinutes()); }
    private long maxPdfBytes() { return Math.multiplyExact((long) materialProperties.getMaxPdfSizeMb(), 1024L * 1024L); }
    private CourseVideoUploadInitResponse uploadResponse(CourseMaterial m){return videoUploadResponse(m,0,null);} private CourseVideoUploadInitResponse videoUploadResponse(CourseMaterial m,long expiry,String signature){String uploadUrl=m.getBunnyVideoId()==null?null:String.format("https://video.bunnycdn.com/library/%s/videos/%s",m.getBunnyLibraryId(),m.getBunnyVideoId());return CourseVideoUploadInitResponse.builder().materialId(m.getId()).bunnyLibraryId(m.getBunnyLibraryId()).bunnyVideoId(m.getBunnyVideoId()).uploadUrl(uploadUrl).authorizationSignature(signature).expirationTimestamp(expiry).uploadMetadata(new ProviderNeutralUploadMetadata(m.getId(),null,uploadUrl,expiry>0?Instant.ofEpochSecond(expiry):null,"COURSE_VIDEO",java.util.Map.of())).build();}
}
