package com.fptu.exe.skillswap.modules.academic.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin chi tiết hồ sơ học thuật của sinh viên/cựu sinh viên")
public class StudentProfileResponse {
    @Schema(description = "ID của người dùng", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID userId;
    
    @Schema(description = "Email người dùng", example = "nguyenvana@gmail.com")
    private String email;
    
    @Schema(description = "Mã số sinh viên", example = "SE192621")
    private String studentCode;
    
    @Schema(description = "Tên hiển thị", example = "Nguyễn Văn A")
    private String displayName;
    
    @Schema(description = "URL ảnh đại diện", example = "https://example.com/avatar.jpg")
    private String avatarUrl;
    
    @Schema(description = "Thông tin cơ sở FPT đang học")
    private CampusResponse campus;
    
    @Schema(description = "Thông tin ngành học")
    private AcademicProgramResponse program;
    
    @Schema(description = "Thông tin chuyên ngành")
    private SpecializationResponse specialization;
    
    @Schema(description = "Học kỳ hiện tại", example = "5")
    private Integer semester;
    
    @Schema(description = "Năm nhập học", example = "2019")
    private Integer intakeYear;
    
    @Schema(description = "Cờ xác nhận cựu sinh viên", example = "false")
    private boolean isAlumni;
    
    @Schema(description = "Năm tốt nghiệp (nếu là cựu sinh viên)", example = "2023")
    private Integer graduationYear;
    
    @Schema(description = "Tiểu sử / giới thiệu bản thân", example = "Sinh viên ngành SE, muốn học AI")
    private String bio;
    
    @Schema(description = "Thời gian tạo hồ sơ", example = "2026-06-22T10:15:30")
    private LocalDateTime createdAt;
    
    @Schema(description = "Thời gian cập nhật hồ sơ gần nhất", example = "2026-06-22T10:15:30")
    private LocalDateTime updatedAt;
}
