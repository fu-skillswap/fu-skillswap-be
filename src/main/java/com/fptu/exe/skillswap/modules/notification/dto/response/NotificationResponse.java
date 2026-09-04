package com.fptu.exe.skillswap.modules.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông báo hiển thị trong trung tâm thông báo của tài khoản hiện tại. REST trả dữ liệu thông báo; realtime có thể bổ sung unreadCount và realtimeEventKind.")
public class NotificationResponse {
    @Schema(description = "Mã định danh của thông báo", example = "019f8234-aaaa-bbbb-cccc-1234567890ab")
    private UUID notificationId;
    @Schema(description = "Mã loại thông báo để FE chọn cách hiển thị; không dùng làm nội dung hiển thị trực tiếp.", example = "BOOKING_ACCEPTED")
    private String type;
    @Schema(description = "Tiêu đề ngắn của thông báo", example = "Yêu cầu đặt lịch đã được chấp nhận")
    private String title;
    @Schema(description = "Nội dung chính của thông báo", example = "Nguyen Van B đã chấp nhận lịch mentoring của bạn.")
    private String message;
    @Schema(description = "Loại đối tượng liên quan để FE điều hướng khi người dùng bấm thông báo", example = "BOOKING")
    private String relatedEntityType;
    @Schema(description = "Mã đối tượng liên quan để FE mở màn hình chi tiết", example = "019f4234-aaaa-bbbb-cccc-1234567890ab")
    private UUID relatedEntityId;
    @Schema(description = "Đường dẫn chi tiết để FE thực hiện điều hướng", example = "/bookings/019f...")
    private String deepLink;
    @Schema(description = "Loại hành động mà FE nên hiển thị hoặc thực hiện", example = "VIEW_BOOKING")
    private String actionType;
    @Schema(description = "Cho biết người dùng đã đọc thông báo hay chưa", example = "false")
    private boolean read;
    @Schema(description = "Thời điểm đọc thông báo, theo UTC và định dạng ISO-8601; null nếu chưa đọc", nullable = true, example = "2026-06-24T05:00:00Z")
    private Instant readAt;
    @Schema(description = "Thời điểm tạo thông báo, theo UTC và định dạng ISO-8601", example = "2026-06-24T04:45:00Z")
    private Instant createdAt;
    @Schema(description = "Chỉ dùng trong realtime event: số thông báo chưa đọc tại thời điểm phát event. API REST có thể trả null.", nullable = true, example = "3")
    private Long unreadCount;
    @Schema(description = "Chỉ dùng trong realtime event: CREATED, READ hoặc READ_ALL. API REST có thể trả null.", nullable = true, example = "CREATED")
    private String realtimeEventKind;
}
