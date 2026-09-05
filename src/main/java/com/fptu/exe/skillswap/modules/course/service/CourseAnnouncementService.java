package com.fptu.exe.skillswap.modules.course.service;

import com.fptu.exe.skillswap.modules.course.domain.Course;
import com.fptu.exe.skillswap.modules.course.domain.CourseAnnouncement;
import com.fptu.exe.skillswap.modules.course.domain.EnrollmentStatus;
import com.fptu.exe.skillswap.modules.course.dto.request.CreateCourseAnnouncementRequest;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseAnnouncementResponse;
import com.fptu.exe.skillswap.modules.course.repository.CourseAnnouncementRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseRepository;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.identity.port.UserSummaryRecord;
import com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxEventTypes;
import com.fptu.exe.skillswap.modules.course.domain.CourseStatus;
import com.fptu.exe.skillswap.modules.course.domain.CourseOutboxEvent;
import com.fptu.exe.skillswap.modules.course.repository.CourseOutboxEventRepository;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseAnnouncementService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final CourseAnnouncementRepository announcementRepository;
    private final CourseRepository courseRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final CourseOutboxEventRepository outboxEventRepository;
    private final UserQueryPort userQueryPort;
    private final com.fptu.exe.skillswap.modules.mentor.port.MentorOwnershipQueryPort mentorOwnershipQueryPort;
    private final TimeProvider timeProvider;

    @Transactional(readOnly = true)
    public Page<CourseAnnouncementResponse> getAnnouncements(UUID userId, UUID courseId, Integer page, Integer size) {
        requireActiveUser(userId);
        Course course = getCourse(courseId);
        requireCourseReader(userId, course);

        int safePage = Math.max(0, page == null ? 0 : page);
        int safeSize = size == null || size <= 0
                ? DEFAULT_PAGE_SIZE
                : Math.min(size, MAX_PAGE_SIZE);

        return announcementRepository
                .findByCourseIdAndDeletedAtIsNullOrderByPublishedAtDescIdDesc(
                        courseId,
                        PageRequest.of(safePage, safeSize))
                .map(this::toResponse);
    }

    @Transactional
    public CourseAnnouncementResponse createAnnouncement(UUID userId, UUID courseId,
                                                         CreateCourseAnnouncementRequest request) {
        Course course = getCourse(courseId);
        UserSummaryRecord user = requireActiveUser(userId);
        requireCourseOwner(userId, course, user);
        requireAnnouncementWritable(course);

        Instant now = timeProvider.instant();
        CourseAnnouncement announcement = CourseAnnouncement.builder()
                .course(course)
                .authorUserId(userId)
                .title(request.title().trim())
                .content(request.content().trim())
                .createdAt(now)
                .updatedAt(now)
                .publishedAt(now)
                .build();

        CourseAnnouncement saved = announcementRepository.save(announcement);
        outboxEventRepository.save(CourseOutboxEvent.builder()
                .aggregateType("CourseAnnouncement")
                .aggregateId(saved.getId())
                .eventType(DomainEventOutboxEventTypes.COURSE_ANNOUNCEMENT_CREATED)
                .payloadJson("{}")
                .status("PENDING")
                .build());
        return toResponse(saved);
    }

    private Course getCourse(UUID courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
    }

    private UserSummaryRecord requireActiveUser(UUID userId) {
        return userQueryPort.findUserSummaryById(userId)
                .filter(UserSummaryRecord::isActive)
                .orElseThrow(() -> new AccessDeniedException("Only active users can access course announcements"));
    }

    private void requireCourseOwner(UUID userId, Course course, UserSummaryRecord user) {
        if (!Objects.equals(course.getMentorUserId(), userId)
                || !user.hasRole(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTOR)
                || !mentorOwnershipQueryPort.isActiveOwner(course.getMentorUserId(), userId)) {
            throw new AccessDeniedException("Only the course mentor can create announcements");
        }
    }

    private void requireAnnouncementWritable(Course course) {
        if (course.getStatus() != CourseStatus.OPEN_FOR_ENROLLMENT
                && course.getStatus() != CourseStatus.REGISTRATION_CLOSED
                && course.getStatus() != CourseStatus.IN_PROGRESS) {
            throw new BaseException(ErrorCode.COURSE_INVALID_STATUS);
        }
    }

    private void requireCourseReader(UUID userId, Course course) {
        if (Objects.equals(course.getMentorUserId(), userId)) {
            return;
        }
        boolean enrolled = enrollmentRepository.existsByCourseIdAndStudentUserIdAndStatusIn(
                course.getId(), userId, List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED));
        if (!enrolled) {
            throw new BaseException(ErrorCode.COURSE_ACCESS_DENIED)
                    .withLogContext("courseId", course.getId());
        }
    }

    private CourseAnnouncementResponse toResponse(CourseAnnouncement announcement) {
        return new CourseAnnouncementResponse(
                announcement.getId(),
                announcement.getCourse().getId(),
                announcement.getAuthorUserId(),
                announcement.getTitle(),
                announcement.getContent(),
                announcement.getCreatedAt(),
                announcement.getUpdatedAt(),
                announcement.getPublishedAt());
    }
}
