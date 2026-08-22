package com.fptu.exe.skillswap.modules.mentor.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceResourceVisibility;
import jakarta.validation.constraints.*;

@Schema(description = "Cập nhật thông tin tài nguyên dịch vụ")
public record MentorServiceResourceUpdateRequest(
        @Schema(description = "Tiêu đề tài liệu", example = "Slide cập nhật v2", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max=255) String title,

        @Schema(description = "Mô tả tài liệu", example = "Đã bổ sung thêm phần bài tập thực hành")
        @Size(max=4000) String description,

        @Schema(
                description = "Phân quyền xem tài liệu (Bắt buộc):<br/>"
                        + "• `AUTHENTICATED`: Người dùng đăng nhập xem được<br/>"
                        + "• `BOOKED_MEMBERS`: Chỉ mentee đã đặt lịch mới xem được",
                example = "BOOKED_MEMBERS",
                allowableValues = {"AUTHENTICATED", "BOOKED_MEMBERS"},
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull MentorServiceResourceVisibility visibility,

        @Schema(description = "Version hiện tại của resource để đảm bảo optimistic locking", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @PositiveOrZero Integer expectedVersion
) {}

