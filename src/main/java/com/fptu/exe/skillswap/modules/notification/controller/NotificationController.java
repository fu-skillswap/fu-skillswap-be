package com.fptu.exe.skillswap.modules.notification.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.notification.dto.response.NotificationResponse;
import com.fptu.exe.skillswap.modules.notification.dto.response.UnreadCountResponse;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/me/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification", description = "Nhóm API đọc danh sách thông báo, unread count và cập nhật trạng thái đã đọc của user hiện tại. FE dùng để dựng badge, dropdown và trang notification history.")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(
            summary = "Lấy danh sách notification của tôi",
            description = "Trả về danh sách thông báo của user hiện tại. FE dùng cho trang notifications hoặc dropdown thông báo, và có thể bật filter unreadOnly khi chỉ muốn lấy các item chưa đọc."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> getMyNotifications(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @ParameterObject Pageable pageable) {
        ensureAuthenticated(principal);
        PageResponse<NotificationResponse> response = notificationService.getMyNotifications(principal.getPublicId(), unreadOnly, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "Lấy số lượng notification chưa đọc",
            description = "Trả về số lượng thông báo chưa đọc của user hiện tại. FE dùng để hiển thị badge mà không cần load toàn bộ danh sách notification."
    )
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getMyUnreadCount(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        ensureAuthenticated(principal);
        long count = notificationService.getMyUnreadCount(principal.getPublicId());
        return ResponseEntity.ok(ApiResponse.success(new UnreadCountResponse(count)));
    }

    @Operation(
            summary = "Đánh dấu notification đã đọc",
            description = "Đánh dấu một notification là đã đọc cho user hiện tại. FE dùng sau khi user mở hoặc xác nhận một thông báo cụ thể."
    )
    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        ensureAuthenticated(principal);
        notificationService.markAsRead(principal.getPublicId(), id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().timestamp(java.time.LocalDateTime.now()).status(200).code("SUCCESS").message("Đánh dấu đã đọc thành công").build());
    }

    @Operation(
            summary = "Đánh dấu tất cả notification đã đọc",
            description = "Đánh dấu tất cả notification là đã đọc cho user hiện tại. FE dùng khi có action đọc hết trong notification center."
    )
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        ensureAuthenticated(principal);
        notificationService.markAllAsRead(principal.getPublicId());
        return ResponseEntity.ok(ApiResponse.<Void>builder().timestamp(java.time.LocalDateTime.now()).status(200).code("SUCCESS").message("Đánh dấu tất cả đã đọc thành công").build());
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
