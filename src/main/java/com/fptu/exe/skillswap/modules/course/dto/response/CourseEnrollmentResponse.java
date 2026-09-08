package com.fptu.exe.skillswap.modules.course.dto.response;

import com.fptu.exe.skillswap.modules.course.domain.CourseEnrollment;
import com.fptu.exe.skillswap.modules.course.domain.EnrollmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Thông tin enrollment khóa học vừa được tạo.")
public record CourseEnrollmentResponse(
        @Schema(description = "ID enrollment.")
        UUID enrollmentId,
        @Schema(description = "ID khóa học.")
        UUID courseId,
        @Schema(description = "Trạng thái enrollment.")
        EnrollmentStatus status,
        @Schema(description = "Thời điểm đăng ký theo UTC.")
        Instant enrolledAt,
        @Schema(description = "Thời điểm hoàn thành, nếu đã hoàn thành.", nullable = true)
        Instant completedAt
) {
    public static CourseEnrollmentResponse from(CourseEnrollment enrollment) {
        return new CourseEnrollmentResponse(
                enrollment.getId(),
                enrollment.getCourse().getId(),
                enrollment.getStatus(),
                enrollment.getEnrolledAt(),
                enrollment.getCompletedAt());
    }
}
