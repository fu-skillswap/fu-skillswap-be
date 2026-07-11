package com.fptu.exe.skillswap.modules.mentor.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Builder
@Schema(description = "Thông tin chi tiết của mentor hiển thị trên trang cá nhân của mentor")
public record MentorDiscoveryDetailResponse(
        @Schema(description = "ID của mentor")
        UUID mentorUserId,
        @Schema(description = "Tên hiển thị của mentor")
        String displayName,
        @Schema(description = "Đường dẫn avatar của mentor")
        String avatarUrl,
        @Schema(description = "Tiêu đề cá nhân ngắn gọn")
        String headline,
        @Schema(description = "Giới thiệu bản thân (từ StudentProfile)")
        String bio,
        @Schema(description = "Mô tả chi tiết về chuyên môn của mentor")
        String expertiseDescription,
        @Schema(description = "Môn - điểm mentor dùng cho peer matching")
        List<MentorSubjectResultResponse> subjectResults,
        @Schema(description = "Mức mentor có thể giúp mentee lấy gốc, 1-4")
        Integer foundationSupportLevel,
        @Schema(description = "Mức mentor có thể review bài nộp/project/CV/report, 1-4")
        Integer outputReviewSupportLevel,
        @Schema(description = "Mức mentor có thể hỗ trợ định hướng/OJT/career, 1-4")
        Integer directionSupportLevel,
        @Schema(description = "Dự án tiêu biểu optional của mentor")
        List<MentorFeaturedProjectResponse> featuredProjects,
        @Schema(description = "Học vấn/giải thưởng optional của mentor")
        List<MentorAchievementResponse> achievements,
        @Schema(description = "Cờ đánh dấu mentor đang mở lịch nhận request")
        Boolean isAvailable,
        @Schema(description = "Thời gian mentor bị đình chỉ nhận booking (nếu có)")
        LocalDateTime bookingSuspendedUntil,
        @Schema(description = "Điểm đánh giá trung bình từ mentee")
        BigDecimal ratingAverage,
        @Schema(description = "Số lượt đánh giá đã nhận")
        Integer reviewCount,
        @Schema(description = "Số lượng phiên mentoring đã hoàn thành")
        Integer completedSessions,
        @Schema(description = "Thời gian mentor được admin xác thực")
        LocalDateTime verifiedAt,
        @Schema(description = "ID cơ sở đào tạo")
        UUID campusId,
        @Schema(description = "Tên cơ sở đào tạo")
        String campusName,
        @Schema(description = "ID chương trình học")
        UUID programId,
        @Schema(description = "Tên chương trình học")
        String programName,
        @Schema(description = "ID chuyên ngành")
        UUID specializationId,
        @Schema(description = "Tên chuyên ngành")
        String specializationName,
        @Schema(description = "Học kỳ hiện tại")
        Integer semester,
        @Schema(description = "Cựu sinh viên hay chưa")
        Boolean alumni,
        @Schema(description = "Link Portfolio")
        String portfolioUrl,
        @Schema(description = "Link Github")
        String githubUrl,
        @Schema(description = "Danh sách chủ đề hướng dẫn/help topics mà mentor hỗ trợ")
        List<MentorTagResponse> helpTopicTags,
        @Schema(description = "Danh sách dịch vụ do mentor cung cấp")
        List<MentorServiceResponse> services,
        @Schema(description = "true nếu mentee có thể gửi yêu cầu book lịch cho mentor này")
        boolean canRequestBooking,
        @Schema(description = "true nếu mentor đã hoàn thành đầy đủ profile của mình")
        boolean hasCompletedProfile,
        @Schema(description = "true nếu mentor đang có ít nhất 1 service active")
        boolean hasActiveServices
) {
}
