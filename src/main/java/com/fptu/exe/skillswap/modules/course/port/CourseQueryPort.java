package com.fptu.exe.skillswap.modules.course.port;

import java.util.Optional;
import java.util.UUID;

public interface CourseQueryPort {

    Optional<String> findCourseTitleById(UUID courseId);

    boolean existsById(UUID courseId);

    boolean isUserEnrolledInCourse(UUID courseId, UUID userId);

    Optional<CourseChatContext> findCourseChatContext(UUID courseId);

    record CourseChatContext(UUID courseId, UUID mentorUserId) {
    }
}
