package com.fptu.exe.skillswap.modules.course.dto.response;

import com.fptu.exe.skillswap.modules.course.domain.CourseMaterialType;
import com.fptu.exe.skillswap.modules.course.domain.MaterialStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Cấu trúc chương trình học và tiến độ của người học. Các thời điểm dùng UTC/ISO-8601 nếu có.")
public record CourseCurriculumResponse(
        @Schema(description = "ID khóa học.", example = "019f1234-aaaa-bbbb-cccc-1234567890ab")
        UUID courseId,
        @Schema(description = "Tiến độ tổng quan của người học.")
        CourseProgressView progress,
        @Schema(description = "Danh sách chương theo thứ tự hiển thị.")
        List<Chapter> chapters
) {
    @Schema(description = "Tiến độ học của tài khoản hiện tại.")
    public record CourseProgressView(
            @Schema(description = "Phần trăm hoàn thành từ 0 đến 100.", example = "35") int overallPercentage,
            @Schema(description = "Tài liệu được học gần nhất; có thể null khi chưa bắt đầu.", nullable = true) UUID lastStudiedMaterialId) {
    }

    @Schema(description = "Một chương và các tài liệu bên trong.")
    public record Chapter(
            @Schema(description = "ID chương.", example = "019f2234-aaaa-bbbb-cccc-1234567890ab") UUID chapterId,
            @Schema(description = "Tên chương hiển thị.", example = "Spring Boot cơ bản") String title,
            @Schema(description = "Mô tả chương; có thể rỗng.", nullable = true) String description,
            @Schema(description = "Vị trí hiển thị của chương.", example = "1") int sortOrder,
            @Schema(description = "Chương đã được công bố cho người học hay chưa.", example = "true") boolean published,
            @Schema(description = "Technical lifecycle field - dùng để tránh ghi đè thay đổi của người khác khi cập nhật chương.", example = "3") long version,
            @Schema(description = "Danh sách tài liệu trong chương.") List<Material> materials) {
    }

    @Schema(description = "Một tài liệu trong chương.")
    public record Material(
            @Schema(description = "ID tài liệu.", example = "019f3234-aaaa-bbbb-cccc-1234567890ab") UUID materialId,
            @Schema(description = "Tên tài liệu.", example = "Dependency Injection") String title,
            @Schema(description = "Loại tài liệu, ví dụ VIDEO hoặc PDF.", example = "VIDEO") CourseMaterialType type,
            @Schema(description = "Vị trí hiển thị trong chương.", example = "1") int sortOrder,
            @Schema(description = "Người học có thể xem trước khi có quyền đầy đủ hay không.", example = "false") boolean previewable,
            @Schema(description = "Tài liệu đã được công bố hay chưa.", example = "true") boolean published,
            @Schema(description = "Trạng thái xử lý tài liệu; READY nghĩa là sẵn sàng sử dụng.", example = "READY") MaterialStatus status,
            @Schema(description = "Thời lượng video bằng giây; PDF có thể null.", example = "600", nullable = true) Integer durationSeconds,
            @Schema(description = "Ảnh đại diện video nếu có.", nullable = true) String thumbnailUrl,
            @Schema(description = "Quyền truy cập hiện tại. AVAILABLE: FE có thể mở material nếu status=READY; LOCKED: chỉ hiển thị thông tin khóa/preview và không gọi playback/download. User chưa enrollment hợp lệ thường nhận LOCKED.", example = "AVAILABLE") String access,
            @Schema(description = "Phần trăm người học đã hoàn thành tài liệu, từ 0 đến 100.", example = "50", nullable = true) Integer progressPercentage,
            @Schema(description = "Tài liệu đã hoàn thành hay chưa.", example = "false") boolean completed,
            @Schema(description = "Technical lifecycle field - dùng để tránh ghi đè thay đổi khi cập nhật tài liệu.", example = "2") long version) {
    }
}
