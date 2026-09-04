package com.fptu.exe.skillswap.modules.notification.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Trạng thái xử lý notification nội bộ. Với FE, trạng thái đọc được biểu thị bằng field read/readAt: read=false là UNREAD và read=true là READ. PENDING/SENDING/SENT/FAILED/FATAL_ERROR chủ yếu dành cho hệ thống, không dùng để quyết định UI notification history.")
public enum NotificationStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED,
    READ,
    FATAL_ERROR
}
