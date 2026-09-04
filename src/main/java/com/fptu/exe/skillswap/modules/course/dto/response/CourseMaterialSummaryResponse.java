package com.fptu.exe.skillswap.modules.course.dto.response;

import com.fptu.exe.skillswap.modules.course.domain.MaterialStatus;
import com.fptu.exe.skillswap.modules.course.domain.CourseMaterialType;
import com.fptu.exe.skillswap.modules.course.domain.StorageProviderType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Thông tin tóm tắt một tài liệu trong khóa học. FE dùng để hiển thị danh sách, trạng thái xử lý và quyền truy cập.")
public class CourseMaterialSummaryResponse {
    @Schema(description = "ID tài liệu.", example = "019f1234-aaaa-bbbb-cccc-1234567890ab")
    private UUID materialId;
    @Schema(description = "ID chương chứa tài liệu.", example = "019f2234-aaaa-bbbb-cccc-1234567890ab")
    private UUID chapterId;
    @Schema(description = "Tên tài liệu hiển thị cho người dùng.", example = "Giới thiệu Spring Boot")
    private String title;
    @Schema(description = "Loại tài liệu được hỗ trợ, ví dụ VIDEO hoặc PDF.", example = "VIDEO")
    private CourseMaterialType materialType;
    @Schema(description = "Internal field - FE không cần sử dụng. Nhà cung cấp lưu trữ hiện tại của tài liệu.", example = "BUNNY_VIDEO")
    private StorageProviderType storageProviderType;
    @Schema(description = "Trạng thái xử lý tài liệu. READY nghĩa là có thể sử dụng; UPLOADING/PROCESSING nghĩa là cần chờ; FAILED/CANCELLED/EXPIRED nghĩa là chưa sẵn sàng.", example = "READY")
    private MaterialStatus status;
    @Schema(description = "Thời lượng video tính bằng giây; PDF có thể null.", example = "420", nullable = true)
    private Integer durationSeconds;
    @Schema(description = "Ảnh đại diện video nếu có.", example = "https://cdn.example/thumbnail.jpg", nullable = true)
    private String thumbnailUrl;
    @Schema(description = "Thời điểm upload hoàn tất, theo UTC.", example = "2026-09-04T10:15:30Z", nullable = true)
    private Instant uploadedAt;
    @Schema(description = "Cho biết tài khoản hiện tại có thể mở tài liệu hay không.", example = "true")
    private boolean available;
    @Schema(description = "Lý do tài liệu bị khóa hoặc chưa thể mở, ví dụ NOT_ENROLLED. FE nên ưu tiên hiển thị userActionMessage và không tự suy đoán từ status.", example = "NOT_ENROLLED", nullable = true)
    private String lockedReason;
    @Schema(description = "Thông báo an toàn để FE hiển thị khi tài liệu chưa sẵn sàng hoặc bị khóa; không chứa lỗi nội bộ.", nullable = true)
    private String userActionMessage;
    @Schema(description = "Cho biết FE có thể thử lại thao tác hiển thị/tải tài liệu hay không. Không tự động tạo bản upload mới.", example = "true")
    private boolean retryable;

    public static String userActionMessage(boolean available, MaterialStatus status) {
        if (!available) return "Bạn chưa có quyền truy cập tài liệu này.";
        if (status == null || status == MaterialStatus.READY) return null;
        return switch (status) {
            case UPLOADING_INTENT, UPLOADING, PROCESSING -> "Tài liệu đang được xử lý. Vui lòng thử lại sau.";
            case FAILED, CANCELLED, EXPIRED, DELETING, DELETED -> "Tài liệu chưa sẵn sàng. Vui lòng thử lại hoặc liên hệ mentor.";
            case READY -> null;
        };
    }

    public static boolean retryable(boolean available, MaterialStatus status) {
        return available && status != null && status != MaterialStatus.READY
                && status != MaterialStatus.DELETED && status != MaterialStatus.DELETING;
    }
}
