package com.fptu.exe.skillswap.modules.admin.dto.request;

import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Getter
@Setter
@Schema(description = "Bộ lọc phân trang cho email outbox nội bộ.")
public class AdminEmailOutboxListRequest extends BasePageRequest {

    @Schema(description = "Lọc theo trạng thái email outbox", example = "FAILED")
    private String status;

    @Schema(description = "Lọc theo mã template", example = "BOOKING_ACCEPTED_EMAIL")
    private String templateCode;

    @Schema(description = "Lọc theo email người nhận (contains, case-insensitive)", example = "skillswap.asia")
    private String toEmail;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Schema(description = "Mốc thời gian bắt đầu theo createdAt (ISO-8601)", example = "2026-07-01T00:00:00")
    private LocalDateTime from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Schema(description = "Mốc thời gian kết thúc theo createdAt (ISO-8601)", example = "2026-07-02T23:59:59")
    private LocalDateTime to;
}
