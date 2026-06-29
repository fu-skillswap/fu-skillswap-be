package com.fptu.exe.skillswap.modules.mentor.dto.response;

import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Builder
@Schema(description = "Thông tin tóm tắt của mentor hiển thị trên thẻ card trang Khám phá")
public record MentorDiscoveryCardResponse(
        @Schema(description = "ID của mentor", example = "019f1234-aaaa-bbbb-cccc-1234567890ab")
        UUID mentorUserId,
        @Schema(description = "Tên hiển thị của mentor", example = "Nguyễn Văn A")
        String displayName,
        @Schema(description = "Đường dẫn avatar của mentor", example = "https://example.com/avatar.jpg")
        String avatarUrl,
        @Schema(description = "Tiêu đề cá nhân ngắn gọn", example = "Senior Backend Developer, Spring Boot Expert")
        String headline,
        @Schema(description = "Mô tả chi tiết về chuyên môn", example = "Có 2 năm kinh nghiệm làm Java Spring Boot...")
        String expertiseDescription,
        @Schema(description = "Các môn học hỗ trợ mentoring", example = "PRJ301, SWP391")
        String supportingSubjects,
        @Schema(description = "Cờ đánh dấu mentor đang mở lịch nhận request (discoverable)", example = "true")
        Boolean isAvailable,
        @Schema(description = "Điểm đánh giá trung bình từ mentee", example = "4.8")
        BigDecimal ratingAverage,
        @Schema(description = "Số lượt đánh giá đã nhận", example = "12")
        Integer reviewCount,
        @Schema(description = "Số lượng phiên mentoring đã hoàn thành", example = "15")
        Integer completedSessions,
        @Schema(description = "Hình thức mentoring (ONLINE, OFFLINE, HYBRID)", example = "ONLINE")
        TeachingMode teachingMode,
        @Schema(description = "Thời gian mentor được admin xác thực", example = "2026-05-15T10:00:00")
        LocalDateTime verifiedAt,
        @Schema(description = "ID cơ sở đào tạo", example = "019f1234-aaaa-bbbb-cccc-1234567890ab")
        UUID campusId,
        @Schema(description = "Tên cơ sở đào tạo", example = "FPT University Hồ Chí Minh")
        String campusName,
        @Schema(description = "ID chương trình học", example = "019f1234-aaaa-bbbb-cccc-1234567890ab")
        UUID programId,
        @Schema(description = "Tên chương trình học", example = "Công nghệ thông tin")
        String programName,
        @Schema(description = "ID chuyên ngành", example = "019f1234-aaaa-bbbb-cccc-1234567890ab")
        UUID specializationId,
        @Schema(description = "Tên chuyên ngành", example = "Kỹ thuật phần mềm")
        String specializationName,
        @Schema(description = "Điểm phù hợp theo ngữ cảnh search hiện tại, đã quy đổi theo phần trăm 0-100", example = "87.50")
        BigDecimal matchScore,
        @Schema(description = "Danh sách chủ đề hướng dẫn/help topics mà mentor hỗ trợ")
        List<MentorTagResponse> helpTopicTags
) {
}
