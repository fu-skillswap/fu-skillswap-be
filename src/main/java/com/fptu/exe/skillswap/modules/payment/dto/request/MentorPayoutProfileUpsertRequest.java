package com.fptu.exe.skillswap.modules.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Thông tin tài khoản nhận payout của mentor. Backend gắn profile với mentor đang đăng nhập và phải kiểm tra lại quyền sở hữu.")
public record MentorPayoutProfileUpsertRequest(
        @NotBlank(message = "accountHolderName không được để trống")
        @Schema(description = "Tên chủ tài khoản ngân hàng.", requiredMode = Schema.RequiredMode.REQUIRED)
        String accountHolderName,

        @Schema(description = "Mã ngân hàng nếu FE chọn từ danh mục; backend nên đối chiếu với bankName.", nullable = true)
        String bankCode,

        @NotBlank(message = "bankName không được để trống")
        @Schema(description = "Tên ngân hàng nhận tiền.", requiredMode = Schema.RequiredMode.REQUIRED)
        String bankName,

        @NotBlank(message = "accountNumber không được để trống")
        @Schema(description = "Số tài khoản nhận tiền. Đây là dữ liệu nhạy cảm; không log ở FE.", requiredMode = Schema.RequiredMode.REQUIRED)
        String accountNumber,

        @Schema(description = "Đặt profile làm mặc định; backend bảo đảm mỗi mentor chỉ có một profile mặc định.", nullable = true)
        Boolean isDefault,

        @Schema(description = "Trạng thái hoạt động do backend kiểm tra và áp dụng; FE không nên tự coi giá trị này là quyền sử dụng.", nullable = true)
        Boolean isActive
) {
}
