package com.fptu.exe.skillswap.modules.admin.dto.request;

import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Bộ lọc queue workbench cho admin dashboard.")
public class AdminQueueCaseListRequest extends BasePageRequest {

    @Schema(description = "Queue key cần drill-down", example = "mentor_verification_pending_review", requiredMode = Schema.RequiredMode.REQUIRED)
    private String queueKey;

    @Schema(description = "Chỉ lấy case đang assign cho chính admin hiện tại", example = "false")
    private Boolean assignedToMe;

    @Schema(description = "Chỉ lấy case chưa được assign cho admin nào", example = "false")
    private Boolean unassignedOnly;
}
