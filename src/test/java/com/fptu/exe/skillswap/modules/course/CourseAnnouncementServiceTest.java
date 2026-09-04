package com.fptu.exe.skillswap.modules.course;

import com.fptu.exe.skillswap.modules.course.domain.Course;
import com.fptu.exe.skillswap.modules.course.domain.CourseAnnouncement;
import com.fptu.exe.skillswap.modules.course.domain.EnrollmentStatus;
import com.fptu.exe.skillswap.modules.course.domain.CourseStatus;
import com.fptu.exe.skillswap.modules.course.dto.request.CreateCourseAnnouncementRequest;
import com.fptu.exe.skillswap.modules.course.repository.CourseAnnouncementRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseRepository;
import com.fptu.exe.skillswap.modules.course.service.CourseAnnouncementService;
import com.fptu.exe.skillswap.modules.course.repository.CourseOutboxEventRepository;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.identity.port.UserSummaryRecord;
import com.fptu.exe.skillswap.modules.mentor.port.MentorOwnershipQueryPort;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CourseAnnouncementServiceTest {

    @Mock
    private CourseAnnouncementRepository announcementRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseEnrollmentRepository enrollmentRepository;

    @Mock
    private CourseOutboxEventRepository outboxEventRepository;

    @Mock
    private UserQueryPort userQueryPort;

    @Mock
    private MentorOwnershipQueryPort mentorOwnershipQueryPort;

    @Mock
    private TimeProvider timeProvider;

    @InjectMocks
    private CourseAnnouncementService announcementService;

    private UUID courseId;
    private UUID mentorId;
    private UUID menteeId;
    private Course course;

    @BeforeEach
    void setUp() {
        lenient().when(timeProvider.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        courseId = UUID.randomUUID();
        mentorId = UUID.randomUUID();
        menteeId = UUID.randomUUID();
        course = Course.builder().id(courseId).mentorUserId(mentorId).title("Java")
                .status(CourseStatus.IN_PROGRESS).build();
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        lenient().when(userQueryPort.findUserSummaryById(mentorId)).thenReturn(Optional.of(
                new UserSummaryRecord(mentorId, "mentor@example.com", "Mentor", null,
                        java.util.Set.of(RoleCode.MENTOR), "ACTIVE", true)));
        lenient().when(userQueryPort.findUserSummaryById(menteeId)).thenReturn(Optional.of(
                new UserSummaryRecord(menteeId, "mentee@example.com", "Mentee", null,
                        java.util.Set.of(RoleCode.MENTEE), "ACTIVE", true)));
        lenient().when(mentorOwnershipQueryPort.isActiveOwner(mentorId, mentorId)).thenReturn(true);
    }

    @Test
    void mentorCanCreateAnnouncement() {
        when(announcementRepository.save(any(CourseAnnouncement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = announcementService.createAnnouncement(
                mentorId, courseId, new CreateCourseAnnouncementRequest(" Lesson ", " New content "));

        assertEquals(courseId, response.courseId());
        assertEquals(mentorId, response.authorUserId());
        assertEquals("Lesson", response.title());
        assertEquals("New content", response.content());
        verify(announcementRepository).save(any(CourseAnnouncement.class));
        verify(outboxEventRepository).save(any());
    }

    @Test
    void menteeCannotCreateAnnouncement() {
        assertThrows(AccessDeniedException.class, () -> announcementService.createAnnouncement(
                menteeId, courseId, new CreateCourseAnnouncementRequest("Title", "Content")));

        verifyNoInteractions(announcementRepository);
    }

    @Test
    void inactiveMentorCannotCreateAnnouncement() {
        when(userQueryPort.findUserSummaryById(mentorId)).thenReturn(Optional.of(
                new UserSummaryRecord(mentorId, "mentor@example.com", "Mentor", null,
                        java.util.Set.of(RoleCode.MENTOR), "INACTIVE", true)));

        assertThrows(AccessDeniedException.class, () -> announcementService.createAnnouncement(
                mentorId, courseId, new CreateCourseAnnouncementRequest("Title", "Content")));

        verifyNoInteractions(announcementRepository, outboxEventRepository);
    }

    @Test
    void mentorCannotCreateAnnouncementAfterCourseCompletion() {
        course.setStatus(CourseStatus.COMPLETED);

        assertThrows(AccessDeniedException.class, () -> announcementService.createAnnouncement(
                mentorId, courseId, new CreateCourseAnnouncementRequest("Title", "Content")));

        verifyNoInteractions(announcementRepository, outboxEventRepository);
    }

    @Test
    void enrolledMenteeCanReadAnnouncements() {
        when(enrollmentRepository.existsByCourseIdAndStudentUserIdAndStatusIn(
                courseId, menteeId, List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED)))
                .thenReturn(true);
        CourseAnnouncement announcement = CourseAnnouncement.builder()
                .id(UUID.randomUUID())
                .course(course)
                .authorUserId(mentorId)
                .title("Welcome")
                .content("Start here")
                .build();
        when(announcementRepository.findByCourseIdAndDeletedAtIsNullOrderByPublishedAtDescIdDesc(
                any(UUID.class), any())).thenReturn(new PageImpl<>(List.of(announcement)));

        var page = announcementService.getAnnouncements(menteeId, courseId, 0, 20);

        assertEquals(1, page.getTotalElements());
        assertEquals("Welcome", page.getContent().getFirst().title());
        verify(announcementRepository).findByCourseIdAndDeletedAtIsNullOrderByPublishedAtDescIdDesc(
                eq(courseId), any());
    }
}
