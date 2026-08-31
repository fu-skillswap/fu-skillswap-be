package com.fptu.exe.skillswap.modules.payment.dto.request;

import com.fptu.exe.skillswap.modules.payment.domain.FundingSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Schema(description = "Request cập nhật campaign cho admin (partial update).")
public record AdminCampaignUpdateRequest(
        @Schema(description = "Tên chiến dịch", example = "Back to School 2026 Updated")
        @Size(max = 150, message = "Tên campaign không quá 150 ký tự")
        String name,

        @Schema(description = "Mô tả chi tiết chiến dịch")
        String description,

        @Schema(description = "Nguồn kinh phí tài trợ")
        FundingSource fundingSource,

        @Schema(description = "Thời điểm bắt đầu chiến dịch")
        LocalDateTime startAt,

        @Schema(description = "Thời điểm kết thúc chiến dịch")
        LocalDateTime endAt,

        @Schema(description = "Ngân sách tối đa của chiến dịch (Scoin)")
        @Min(value = 0, message = "Ngân sách phải >= 0")
        Integer budgetScoin,

        @Schema(description = "Tập hợp mã vai trò áp dụng")
        Set<String> audienceRoleCodes,

        @Schema(description = "Tập hợp campus ID áp dụng")
        Set<UUID> audienceCampusIds,

        @Schema(description = "Tập hợp program ID áp dụng")
        Set<UUID> audienceProgramIds,

        @Schema(description = "Tập hợp specialization ID áp dụng")
        Set<UUID> audienceSpecializationIds
) {
}
