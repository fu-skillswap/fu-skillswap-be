package com.fptu.exe.skillswap.modules.admin.dto.request;

import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Schema(description = "Bộ lọc phân trang cho audit logs nội bộ của admin.")
public class AdminAuditLogListRequest extends BasePageRequest {

    @Schema(description = "Lọc theo actor user id", example = "019f1258-bdb6-7312-ac67-b289909329d1")
    private UUID actorUserId;

    @Schema(description = "Lọc theo entity type", example = "USER")
    private String entityType;

    @Schema(description = "Lọc theo entity id", example = "019f1258-bdb6-7312-ac67-b289909329d1")
    private UUID entityId;

    @Schema(description = "Lọc theo action", example = "UPDATE")
    private String action;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Schema(description = "Mốc thời gian bắt đầu (ISO-8601)", example = "2026-07-01T00:00:00")
    private LocalDateTime from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Schema(description = "Mốc thời gian kết thúc (ISO-8601)", example = "2026-07-02T23:59:59")
    private LocalDateTime to;
}
