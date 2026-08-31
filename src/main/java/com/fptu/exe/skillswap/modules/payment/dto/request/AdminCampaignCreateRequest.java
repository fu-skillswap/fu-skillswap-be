package com.fptu.exe.skillswap.modules.payment.dto.request;

import com.fptu.exe.skillswap.modules.payment.domain.FundingSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Schema(description = "Request tạo campaign mới cho admin.")
public record AdminCampaignCreateRequest(
        @Schema(description = "Tên chiến dịch", example = "Back to School 2026")
        @NotBlank(message = "Tên campaign không được để trống")
        @Size(max = 150, message = "Tên campaign không quá 150 ký tự")
        String name,

        @Schema(description = "Mô tả chi tiết chiến dịch")
        String description,

        @Schema(description = "Nguồn kinh phí tài trợ", example = "APP_FUNDED")
        @NotNull(message = "Nguồn kinh phí tài trợ không được để trống")
        FundingSource fundingSource,

        @Schema(description = "Thời điểm bắt đầu chiến dịch (ISO format). Nếu bắt đầu tương lai thì status sẽ là SCHEDULED.")
        LocalDateTime startAt,

        @Schema(description = "Thời điểm kết thúc chiến dịch (ISO format)")
        LocalDateTime endAt,

        @Schema(description = "Ngân sách tối đa của chiến dịch (Scoin)", example = "100000")
        @NotNull(message = "Ngân sách không được để trống")
        @Min(value = 0, message = "Ngân sách phải >= 0")
        Integer budgetScoin,

        @Schema(description = "Tập hợp mã vai trò áp dụng (VD: STUDENT, MENTOR). Để trống = tất cả")
        Set<String> audienceRoleCodes,

        @Schema(description = "Tập hợp campus ID áp dụng. Để trống = tất cả")
        Set<UUID> audienceCampusIds,

        @Schema(description = "Tập hợp program ID áp dụng. Để trống = tất cả")
        Set<UUID> audienceProgramIds,

        @Schema(description = "Tập hợp specialization ID áp dụng. Để trống = tất cả")
        Set<UUID> audienceSpecializationIds
) {
}
