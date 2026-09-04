package com.fptu.exe.skillswap.modules.course.service;

import com.fptu.exe.skillswap.modules.course.domain.CourseAnnouncement;
import com.fptu.exe.skillswap.modules.course.domain.CourseEnrollment;
import com.fptu.exe.skillswap.modules.course.domain.EnrollmentStatus;
import com.fptu.exe.skillswap.modules.course.repository.CourseAnnouncementRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentRepository;
import com.fptu.exe.skillswap.modules.notification.NotificationType;
import com.fptu.exe.skillswap.modules.notification.port.NotificationCommandPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Processes durable announcement-created events without loading a whole course roster. */
@Service
@RequiredArgsConstructor
public class CourseAnnouncementNotificationService {

    private static final int RECIPIENT_BATCH_SIZE = 100;
    private static final String RELATED_ENTITY_TYPE = "COURSE_ANNOUNCEMENT";

    private final CourseAnnouncementRepository announcementRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final NotificationCommandPort notificationCommandPort;

    public void process(UUID announcementId) {
        CourseAnnouncement announcement = announcementRepository.findWithCourseById(announcementId)
                .orElseThrow(() -> new IllegalStateException("Announcement not found: " + announcementId));
        UUID courseId = announcement.getCourse().getId();
        int page = 0;
        Slice<CourseEnrollment> enrollments;
        do {
            enrollments = enrollmentRepository.findByCourseIdAndStatusIn(
                    courseId,
                    List.of(EnrollmentStatus.ACTIVE),
                    PageRequest.of(page++, RECIPIENT_BATCH_SIZE, Sort.by(Sort.Direction.ASC, "id")));
            for (CourseEnrollment enrollment : enrollments) {
                notificationCommandPort.publishIfAbsent(new NotificationCommandPort.NotificationIntent(
                        enrollment.getStudentUserId(),
                        NotificationType.COURSE_ANNOUNCEMENT.name(),
                        announcement.getTitle(),
                        announcement.getContent(),
                        RELATED_ENTITY_TYPE,
                        announcement.getId(),
                        "/courses/" + courseId + "/announcements"));
            }
        } while (enrollments.hasNext());
    }
}
