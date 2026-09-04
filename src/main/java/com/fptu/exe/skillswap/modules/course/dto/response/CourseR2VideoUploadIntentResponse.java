package com.fptu.exe.skillswap.modules.course.dto.response;

import com.fptu.exe.skillswap.modules.course.domain.MaterialStatus;
import com.fptu.exe.skillswap.shared.dto.response.ProviderNeutralUploadMetadata;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Schema(description = "Upload intent video dùng storage provider trung lập. FE upload trực tiếp bằng uploadUrl, sau đó gọi API confirm-video-upload. Không trả bucket, credentials hoặc object key.")
public record CourseR2VideoUploadIntentResponse(
        @Schema(description = "ID asset video. Đồng thời là materialId dùng khi gọi API confirm.", example = "019f1234-aaaa-bbbb-cccc-1234567890ab")
        UUID assetId,
        @Schema(description = "ID upload intent. MVP dùng cùng giá trị với assetId để FE chỉ cần lưu một ID.", example = "019f1234-aaaa-bbbb-cccc-1234567890ab")
        UUID uploadIntentId,
        @Schema(description = "URL presigned upload tạm thời. FE gửi PUT trực tiếp lên URL này và không lưu làm URL playback.", example = "https://storage.example/presigned-put")
        String uploadUrl,
        @Schema(description = "Thời điểm URL hết hạn theo UTC; hết hạn thì tạo intent mới.", example = "2026-09-04T04:00:00Z")
        Instant expiresAt,
        @Schema(description = "MIME type đã được backend chấp nhận.", example = "video/mp4")
        String contentType,
        @Schema(description = "Trạng thái sau khi tạo intent. FE upload xong vẫn phải gọi confirm để chuyển sang READY.", example = "UPLOADING")
        MaterialStatus status,
        @Schema(description = "Header bắt buộc khi FE gửi PUT upload; không tự đổi Content-Type.", example = "{\"Content-Type\":\"video/mp4\"}")
        Map<String, String> requiredHeaders,
        @Schema(description = "Metadata upload trung lập; FE ưu tiên dùng object này khi tích hợp nhiều provider.")
        ProviderNeutralUploadMetadata uploadMetadata
) {
}
