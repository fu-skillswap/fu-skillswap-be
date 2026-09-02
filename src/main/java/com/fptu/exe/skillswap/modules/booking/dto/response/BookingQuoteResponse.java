package com.fptu.exe.skillswap.modules.booking.dto.response;

import com.fptu.exe.skillswap.modules.booking.port.BookingPricingEstimate;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Báo giá tạm tính cho khung giờ mentee đang chọn; chưa giữ chỗ")
public record BookingQuoteResponse(
        @Schema(description = "Khung giờ được chọn")
        UUID slotId,
        @Schema(description = "Dịch vụ mentoring được chọn")
        UUID serviceId,
        @Schema(description = "Tên dịch vụ")
        String serviceTitle,
        @Schema(description = "Thời lượng buổi học, tính bằng phút")
        Integer durationMinutes,
        @Schema(description = "Giờ bắt đầu có kèm offset múi giờ", example = "2026-08-30T19:00:00+07:00")
        OffsetDateTime scheduledStartAt,
        @Schema(description = "Giờ kết thúc có kèm offset múi giờ", example = "2026-08-30T20:00:00+07:00")
        OffsetDateTime scheduledEndAt,
        @Schema(description = "Hạn mentor phản hồi nếu mentee tạo booking ngay lúc này")
        OffsetDateTime pendingExpireAt,
        @Schema(description = "Số phút tối đa để thanh toán sau khi mentor accept", example = "60")
        int paymentWindowMinutes,
        @Schema(description = "Booking phải thanh toán xong trước giờ học ít nhất số phút này", example = "60")
        int paymentPreparationBufferMinutes,
        @Schema(description = "Chi tiết giá backend đã tính")
        BookingPricingEstimate pricing,
        @Schema(description = "Chính sách hoàn/chia tiền hiện hành")
        BookingCancellationRefundPolicyResponse cancellationRefundPolicy,
        @Schema(description = "true vì quote có thể thay đổi khi tạo booking hoặc checkout")
        boolean isEstimate,
        @Schema(description = "Lưu ý ngắn FE cần hiển thị", nullable = true)
        String disclaimer
) {
}
