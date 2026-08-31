package com.fptu.exe.skillswap.modules.course.service;

import com.fptu.exe.skillswap.modules.course.domain.Course;
import com.fptu.exe.skillswap.modules.course.domain.EnrollmentStatus;
import com.fptu.exe.skillswap.modules.course.port.CourseQueryPort;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseQueryPortImpl implements CourseQueryPort {

    private final CourseRepository courseRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;

    @Override
    public Optional<String> findCourseTitleById(UUID courseId) {
        if (courseId == null) {
            return Optional.empty();
        }
        return courseRepository.findById(courseId).map(Course::getTitle);
    }

    @Override
    public boolean existsById(UUID courseId) {
        return courseId != null && courseRepository.existsById(courseId);
    }

    @Override
    public boolean isUserEnrolledInCourse(UUID courseId, UUID userId) {
        if (courseId == null || userId == null) {
            return false;
        }
        return courseEnrollmentRepository.findByCourseIdAndStudentUserId(courseId, userId)
                .map(enrollment -> enrollment.getStatus() == EnrollmentStatus.ACTIVE || enrollment.getStatus() == EnrollmentStatus.COMPLETED)
                .orElse(false);
    }

    @Override
    public Optional<CourseChatContext> findCourseChatContext(UUID courseId) {
        return courseId == null ? Optional.empty() : courseRepository.findById(courseId)
                .map(course -> new CourseChatContext(course.getId(), course.getMentorProfile() == null
                        ? null : course.getMentorProfile().getUserId()));
    }
}
