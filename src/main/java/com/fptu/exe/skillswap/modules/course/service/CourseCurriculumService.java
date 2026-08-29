package com.fptu.exe.skillswap.modules.course.service;

import com.fptu.exe.skillswap.modules.course.domain.*;
import com.fptu.exe.skillswap.modules.course.dto.request.*;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseCurriculumResponse;
import com.fptu.exe.skillswap.modules.course.repository.*;
import com.fptu.exe.skillswap.shared.exception.BadRequestException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Owns the visible tree. It deliberately has no separate lecture layer. */
@Service
@RequiredArgsConstructor
public class CourseCurriculumService {
    private final CourseRepository courseRepository;
    private final CourseChapterRepository chapterRepository;
    private final CourseMaterialRepository materialRepository;
    private final CourseMaterialProgressRepository materialProgressRepository;
    private final CourseProgressRepository courseProgressRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final CourseVaultService vaultService;

    @Transactional
    public CourseChapter createChapter(UUID userId, UUID courseId, CreateCourseChapterRequest request) {
        Course course = ownedCourse(userId, courseId);
        CourseChapter chapter = CourseChapter.builder().course(course).title(request.title()).description(request.description())
                .sortOrder(chapterRepository.countByCourseId(courseId) + 1).isPublished(!Boolean.FALSE.equals(request.published())).build();
        course.setTotalChapters(course.getTotalChapters() + 1);
        return chapterRepository.save(chapter);
    }

    @Transactional
    public CourseChapter updateChapter(UUID userId, UUID courseId, UUID chapterId, UpdateCourseChapterRequest request) {
        CourseChapter chapter = ownedChapter(userId, courseId, chapterId);
        assertVersion(chapter.getId(), chapter.getVersion(), request.expectedVersion());
        chapter.setTitle(request.title()); chapter.setDescription(request.description()); chapter.setPublished(request.published());
        return chapter;
    }

    @Transactional
    public void deleteEmptyChapter(UUID userId, UUID courseId, UUID chapterId) {
        CourseChapter chapter = ownedChapter(userId, courseId, chapterId);
        if (!materialRepository.findByChapterIdAndDeletedAtIsNullOrderBySortOrderAsc(chapterId).isEmpty()) {
            throw new BadRequestException(ErrorCode.RESOURCE_CONFLICT, "Delete or move all materials before deleting a chapter");
        }
        Course course = chapter.getCourse();
        chapterRepository.delete(chapter);
        course.setTotalChapters(Math.max(0, course.getTotalChapters() - 1));
    }

    @Transactional
    public void reorderChapters(UUID userId, UUID courseId, ReorderCurriculumRequest request) {
        Course course = ownedCourse(userId, courseId);
        assertVersion(course.getId(), course.getVersion(), request.expectedContainerVersion());
        List<CourseChapter> chapters = chapterRepository.findByCourseIdOrderBySortOrderAsc(courseId);
        Map<UUID, CourseChapter> byId = chapters.stream().collect(Collectors.toMap(CourseChapter::getId, Function.identity()));
        validateExactIds(byId.keySet(), request.orderedIds(), "chapter");
        // Move through a disjoint temporary range so the database unique constraint is never violated.
        chapters.forEach(chapter -> chapter.setSortOrder(-chapter.getSortOrder() - 1));
        for (int index = 0; index < request.orderedIds().size(); index++) byId.get(request.orderedIds().get(index)).setSortOrder(index + 1);
    }

    @Transactional
    public void reorderMaterials(UUID userId, UUID courseId, UUID chapterId, ReorderCurriculumRequest request) {
        CourseChapter chapter = ownedChapter(userId, courseId, chapterId);
        assertVersion(chapter.getId(), chapter.getVersion(), request.expectedContainerVersion());
        List<CourseMaterial> materials = materialRepository.findByChapterIdAndDeletedAtIsNullOrderBySortOrderAsc(chapterId);
        Map<UUID, CourseMaterial> byId = materials.stream().collect(Collectors.toMap(CourseMaterial::getId, Function.identity()));
        validateExactIds(byId.keySet(), request.orderedIds(), "material");
        materials.forEach(material -> material.setSortOrder(-material.getSortOrder() - 1));
        for (int index = 0; index < request.orderedIds().size(); index++) byId.get(request.orderedIds().get(index)).setSortOrder(index + 1);
    }

    @Transactional
    public CourseMaterial updateMaterial(UUID userId, UUID courseId, UUID materialId, UpdateCourseMaterialRequest request) {
        CourseMaterial material = materialRepository.findActiveWithCurriculumById(materialId).orElseThrow(() -> new ResourceNotFoundException("Course material not found"));
        if (!material.getChapter().getCourse().getId().equals(courseId) || !material.getChapter().getCourse().getMentorProfile().getUserId().equals(userId)) throw new AccessDeniedException("Only course mentor can change curriculum");
        assertVersion(material.getId(), material.getVersion(), request.expectedVersion());
        material.setTitle(request.title()); material.setPreviewable(request.previewable()); material.setPublished(request.published());
        refreshCourseTotal(material.getChapter().getCourse());
        return material;
    }

    @Transactional
    public void deleteMaterial(UUID userId, UUID courseId, UUID materialId) { vaultService.deleteMaterial(userId, courseId, materialId); }

    @Transactional(readOnly = true)
    public CourseCurriculumResponse getCurriculum(UUID userId, UUID courseId) {
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new ResourceNotFoundException("Course not found"));
        boolean mentor = course.getMentorProfile().getUserId().equals(userId);
        boolean enrolled = mentor || hasEntitlement(courseId, userId);
        List<CourseChapter> chapters = mentor ? chapterRepository.findByCourseIdOrderBySortOrderAsc(courseId) : chapterRepository.findByCourseIdAndIsPublishedTrueOrderBySortOrderAsc(courseId);
        Map<UUID, CourseMaterialProgress> progresses = materialProgressRepository.findByStudentUserIdAndCourseId(userId, courseId).stream().collect(Collectors.toMap(p -> p.getMaterial().getId(), Function.identity()));
        List<CourseCurriculumResponse.Chapter> tree = chapters.stream().map(chapter -> new CourseCurriculumResponse.Chapter(chapter.getId(), chapter.getTitle(), chapter.getDescription(), chapter.getSortOrder(), chapter.isPublished(), chapter.getVersion(),
                materialRepository.findByChapterIdAndDeletedAtIsNullOrderBySortOrderAsc(chapter.getId()).stream().filter(material -> mentor || material.isPublished()).map(material -> toMaterial(material, mentor, enrolled, progresses.get(material.getId()))).toList())).toList();
        CourseProgress progress = courseProgressRepository.findByStudentUserIdAndCourseId(userId, courseId).orElse(null);
        return new CourseCurriculumResponse(courseId, new CourseCurriculumResponse.CourseProgressView(progress == null ? 0 : progress.getOverallPercentage(), progress == null || progress.getLastStudiedMaterial() == null ? null : progress.getLastStudiedMaterial().getId()), tree);
    }

    private CourseCurriculumResponse.Material toMaterial(CourseMaterial material, boolean mentor, boolean enrolled, CourseMaterialProgress progress) {
        String access = mentor || material.isPreviewable() || enrolled ? "AVAILABLE" : "LOCKED";
        return new CourseCurriculumResponse.Material(material.getId(), material.getTitle(), material.getMaterialType(), material.getSortOrder(), material.isPreviewable(), material.isPublished(), material.getStatus(), material.getDurationSeconds(), material.getThumbnailUrl(), access, progress == null ? null : progress.getCompletionPercentage(), progress != null && progress.isCompleted(), material.getVersion());
    }
    private boolean hasEntitlement(UUID courseId, UUID userId) { return enrollmentRepository.findByCourseIdAndStudentUserId(courseId, userId).map(e -> e.getStatus() == EnrollmentStatus.ACTIVE || e.getStatus() == EnrollmentStatus.COMPLETED).orElse(false); }
    private Course ownedCourse(UUID userId, UUID courseId) { Course course=courseRepository.findByIdAndMentorProfileUserId(courseId,userId).orElseThrow(() -> new AccessDeniedException("Only course mentor can change curriculum")); return course; }
    private CourseChapter ownedChapter(UUID userId, UUID courseId, UUID chapterId) { CourseChapter chapter=chapterRepository.findById(chapterId).orElseThrow(() -> new ResourceNotFoundException("Chapter not found")); if(!chapter.getCourse().getId().equals(courseId)||!chapter.getCourse().getMentorProfile().getUserId().equals(userId))throw new AccessDeniedException("Only course mentor can change curriculum");return chapter; }
    private void refreshCourseTotal(Course course) { course.setTotalMaterials(Math.toIntExact(materialRepository.countByChapterCourseIdAndDeletedAtIsNullAndIsPublishedTrue(course.getId()))); }
    private void assertVersion(UUID id, Long actual, Long expected) { if (!Objects.equals(actual, expected)) throw new BadRequestException(ErrorCode.RESOURCE_CONFLICT, "Curriculum changed by another request; refresh and retry"); }
    private void validateExactIds(Set<UUID> actual, List<UUID> requested, String label) { if(requested.size()!=actual.size()||new HashSet<>(requested).size()!=requested.size()||!actual.equals(new HashSet<>(requested)))throw new BadRequestException(ErrorCode.BAD_REQUEST,"The " + label + " order must contain every current item exactly once"); }
}
