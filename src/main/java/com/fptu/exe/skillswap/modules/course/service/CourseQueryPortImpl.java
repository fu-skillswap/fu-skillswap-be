package com.fptu.exe.skillswap.modules.course.service;

import com.fptu.exe.skillswap.modules.course.domain.EnrollmentStatus;
import com.fptu.exe.skillswap.modules.course.port.CourseQueryPort;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseQueryPortImpl implements CourseQueryPort {

    private final CourseEnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;

    @Override
    public boolean isStudentEnrolledInCourse(UUID courseId, UUID userId) {
        if (courseId == null || userId == null) {
            return false;
        }
        return enrollmentRepository.findByCourseIdAndStudentUserId(courseId, userId)
                .map(e -> e.getStatus() == EnrollmentStatus.ACTIVE || e.getStatus() == EnrollmentStatus.COMPLETED)
                .orElse(false);
    }

    @Override
    public boolean isCourseActive(UUID courseId) {
        if (courseId == null) {
            return false;
        }
        return courseRepository.existsById(courseId);
    }
}
