package com.fptu.exe.skillswap.modules.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Schema(description = "Payload tạo admin note nội bộ cho một target vận hành.")
public class AdminNoteCreateRequest {

    @NotBlank
    @Schema(description = "Loại target được ghi chú", example = "BOOKING")
    private String targetType;

    @NotNull
    @Schema(description = "Id của target cần ghi chú", example = "019f1258-bdb6-7312-ac67-b289909329d1")
    private UUID targetId;

    @NotBlank
    @Size(max = 5000)
    @Schema(description = "Nội dung ghi chú nội bộ", example = "Đã gọi điện xác minh với user, chờ phản hồi thêm.")
    private String note;
}
