package com.fptu.exe.skillswap.modules.course.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Trạng thái xử lý material. UPLOADING_INTENT: đã tạo intent; UPLOADING: đang upload; PROCESSING: backend/provider đang xử lý; READY: có thể mở nếu access=AVAILABLE; FAILED/CANCELLED/EXPIRED: chưa sẵn sàng; DELETING/DELETED: đang hoặc đã xóa. FE hiển thị theo available, lockedReason và userActionMessage, không tự suy đoán quyền từ status.")
public enum MaterialStatus {
    UPLOADING_INTENT,
    UPLOADING,
    PROCESSING,
    READY,
    FAILED,
    CANCELLED,
    EXPIRED,
    DELETING,
    DELETED
}
