package com.fptu.exe.skillswap.modules.course.port;

import java.util.UUID;

public interface CourseQueryPort {
    boolean isStudentEnrolledInCourse(UUID courseId, UUID userId);
    boolean isCourseActive(UUID courseId);
    String findCourseTitle(UUID courseId);
}
