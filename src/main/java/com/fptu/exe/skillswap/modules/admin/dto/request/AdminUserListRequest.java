package com.fptu.exe.skillswap.modules.admin.dto.request;

import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminUserListRequest extends BasePageRequest {

    @Schema(description = "Từ khóa tìm theo email hoặc họ tên", example = "nguyen van an")
    private String keyword;

    @Schema(description = "Lọc theo role, chỉ chấp nhận MENTEE hoặc MENTOR", example = "MENTOR", allowableValues = {"MENTEE", "MENTOR"})
    private String role;

    @Schema(description = "Lọc theo trạng thái tài khoản", example = "ACTIVE")
    private String status;
}
