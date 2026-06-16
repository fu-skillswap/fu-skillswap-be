package com.fptu.exe.skillswap.modules.academic.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin hồ sơ học thuật cần điền khi đăng nhập lần đầu hoặc cập nhật")
public class StudentProfileRequest {

    @Schema(
        description = "Mã số sinh viên FPT. Format: {H|S|D|Q|C}{E|S|A} + khóa (01–22) + 4 chữ số",
        example = "SE192621",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Mã số sinh viên không được để trống")
    @Pattern(regexp = "^(?i)[HSDQC][ESA](0[1-9]|1[0-9]|2[0-2])\\d{4}$", message = "Mã số sinh viên không đúng định dạng (Ví dụ: SE192621)")
    private String studentCode;

    @Schema(
        description = "Tên hiển thị trên hệ thống (nếu không điền sẽ giữ nguyên tên từ Google)",
        example = "Nguyễn Văn A"
    )
    @Size(max = 150, message = "Tên hiển thị không được quá 150 ký tự")
    private String displayName;

    @Schema(
        description = "URL ảnh đại diện (nếu không điền sẽ giữ nguyên ảnh từ Google)",
        example = "https://example.com/avatar.jpg"
    )
    private String avatarUrl;

    @Schema(
        description = "ID của cơ sở FPT (lấy từ API /api/campuses)",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Cơ sở không được để trống")
    private UUID campusId;

    @Schema(
        description = "ID của ngành học (lấy từ API /api/academic-programs)",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Ngành học không được để trống")
    private UUID programId;

    @Schema(
        description = "ID của chuyên ngành (lấy từ API /api/academic-programs/{programId}/specializations). Phải thuộc ngành học đã chọn.",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Chuyên ngành không được để trống")
    private UUID specializationId;

    @Schema(
        description = "Học kỳ hiện tại (ví dụ: 1, 2, 3, ...)",
        example = "5",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Học kỳ không được để trống")
    private Integer semester;

    @Schema(
        description = "Năm nhập học (ví dụ: 2019 tương ứng khóa K19)",
        example = "2019",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Khóa tuyển sinh không được để trống")
    private Integer intakeYear;

    @Schema(
        description = "true nếu là cựu sinh viên đã tốt nghiệp, false nếu đang học",
        example = "false",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Trạng thái cựu sinh viên không được để trống")
    private Boolean isAlumni;

    @Schema(
        description = "Năm tốt nghiệp (bắt buộc nếu isAlumni = true)",
        example = "2023"
    )
    private Integer graduationYear;

    @Schema(
        description = "Giới thiệu bản thân, kỹ năng nổi bật, mục tiêu trao đổi kỹ năng",
        example = "Mình là sinh viên SE năm 4, mạnh về React và Spring Boot. Muốn học thêm về AI."
    )
    private String bio;
}
