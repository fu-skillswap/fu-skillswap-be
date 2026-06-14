package com.fptu.exe.skillswap.modules.identity.dto.response;

import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Thông tin người dùng đang đăng nhập, bao gồm trạng thái hoàn thành hồ sơ")
public class UserMeResponse {

    @Schema(description = "ID công khai của người dùng", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID publicId;

    @Schema(description = "Địa chỉ email Google", example = "nguyenvana@fpt.edu.vn")
    private String email;

    @Schema(description = "Tên hiển thị", example = "Nguyễn Văn A")
    private String fullName;

    @Schema(description = "URL ảnh đại diện", example = "https://lh3.googleusercontent.com/...")
    private String avatarUrl;

    @Schema(description = "Trạng thái tài khoản: ACTIVE, INACTIVE, BANNED, DELETED", example = "ACTIVE")
    private UserStatus status;

    @Schema(description = "Danh sách vai trò của người dùng (MENTEE, MENTOR, ADMIN, SYSTEM_ADMIN)", example = "[\"MENTEE\"]")
    private List<RoleCode> roles;

    @Schema(
        description = "true nếu người dùng đã hoàn thành hồ sơ học thuật (đã điền StudentProfile). " +
            "FE dùng field này để quyết định chuyển hướng vào dashboard hay trang điền hồ sơ.",
        example = "true"
    )
    private boolean profileCompleted;

    @Schema(
        description = "Alias của profileCompleted – true nếu đã tồn tại StudentProfile cho tài khoản này.",
        example = "true"
    )
    private boolean hasStudentProfile;
}
