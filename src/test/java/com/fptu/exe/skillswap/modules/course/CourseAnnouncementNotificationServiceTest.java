package com.fptu.exe.skillswap.modules.course;

import com.fptu.exe.skillswap.modules.course.domain.Course;
import com.fptu.exe.skillswap.modules.course.domain.CourseAnnouncement;
import com.fptu.exe.skillswap.modules.course.domain.CourseEnrollment;
import com.fptu.exe.skillswap.modules.course.domain.EnrollmentStatus;
import com.fptu.exe.skillswap.modules.course.repository.CourseAnnouncementRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentRepository;
import com.fptu.exe.skillswap.modules.course.service.CourseAnnouncementNotificationService;
import com.fptu.exe.skillswap.modules.notification.port.NotificationCommandPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.SliceImpl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseAnnouncementNotificationServiceTest {

    @Mock
    private CourseAnnouncementRepository announcementRepository;
    @Mock
    private CourseEnrollmentRepository enrollmentRepository;
    @Mock
    private NotificationCommandPort notificationCommandPort;
    @InjectMocks
    private CourseAnnouncementNotificationService notificationService;

    @Test
    void processesOnlyActiveRecipientsInBoundedBatches() {
        UUID courseId = UUID.randomUUID();
        UUID announcementId = UUID.randomUUID();
        Course course = Course.builder().id(courseId).build();
        CourseAnnouncement announcement = CourseAnnouncement.builder()
                .id(announcementId).course(course).title("Lesson").content("Read this").build();
        CourseEnrollment enrollment = CourseEnrollment.builder()
                .id(UUID.randomUUID()).course(course).studentUserId(UUID.randomUUID())
                .status(EnrollmentStatus.ACTIVE).build();
        when(announcementRepository.findWithCourseById(announcementId)).thenReturn(Optional.of(announcement));
        when(enrollmentRepository.findByCourseIdAndStatusIn(any(), any(), any()))
                .thenReturn(new SliceImpl<>(List.of(enrollment), org.springframework.data.domain.PageRequest.of(0, 100), false));

        notificationService.process(announcementId);

        verify(enrollmentRepository).findByCourseIdAndStatusIn(
                eq(courseId), eq(List.of(EnrollmentStatus.ACTIVE)),
                org.mockito.ArgumentMatchers.argThat(pageable -> pageable.getPageSize() == 100));
        verify(notificationCommandPort).publishIfAbsent(any());
    }

    @Test
    void retriesTheSameEventThroughIdempotentNotificationContract() {
        UUID courseId = UUID.randomUUID();
        UUID announcementId = UUID.randomUUID();
        Course course = Course.builder().id(courseId).build();
        CourseAnnouncement announcement = CourseAnnouncement.builder()
                .id(announcementId).course(course).title("Lesson").content("Read this").build();
        CourseEnrollment enrollment = CourseEnrollment.builder()
                .id(UUID.randomUUID()).course(course).studentUserId(UUID.randomUUID())
                .status(EnrollmentStatus.ACTIVE).build();
        when(announcementRepository.findWithCourseById(announcementId)).thenReturn(Optional.of(announcement));
        when(enrollmentRepository.findByCourseIdAndStatusIn(any(), any(), any()))
                .thenReturn(new SliceImpl<>(List.of(enrollment)));

        notificationService.process(announcementId);
        notificationService.process(announcementId);

        verify(notificationCommandPort, times(2)).publishIfAbsent(any());
    }
}
