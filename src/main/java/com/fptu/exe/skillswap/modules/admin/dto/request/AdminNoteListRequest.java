package com.fptu.exe.skillswap.modules.admin.dto.request;

import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Schema(description = "Bộ lọc phân trang cho admin notes nội bộ.")
public class AdminNoteListRequest extends BasePageRequest {

    @Schema(description = "Lọc theo target type", example = "BOOKING")
    private String targetType;

    @Schema(description = "Lọc theo target id", example = "019f1258-bdb6-7312-ac67-b289909329d1")
    private UUID targetId;
}
