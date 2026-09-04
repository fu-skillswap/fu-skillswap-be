package com.fptu.exe.skillswap.shared.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Provider-neutral upload information for frontend clients.
 *
 * <p>Provider-specific fields remain in the existing response models for
 * compatibility. New clients should prefer this object when it is present.</p>
 */
@Schema(description = "Metadata upload trung lập với nhà cung cấp. FE ưu tiên dùng object này; các field provider cũ vẫn được giữ để tương thích.")
public record ProviderNeutralUploadMetadata(
        @Schema(description = "ID asset sau khi upload được xác nhận; có thể null trong bước tạo upload intent.", nullable = true)
        UUID assetId,
        @Schema(description = "ID upload intent do backend tạo; dùng để xác nhận upload.", nullable = true)
        UUID uploadIntentId,
        @Schema(description = "URL tạm thời để upload trực tiếp. FE dùng ngay, không lưu làm URL cố định và không tự sửa giá trị.", nullable = true)
        String url,
        @Schema(description = "Thời điểm URL hết hạn theo UTC; sau thời điểm này FE cần tạo intent mới.", nullable = true)
        Instant expiresAt,
        @Schema(description = "Loại asset nghiệp vụ, ví dụ BLOG_IMAGE, PORTFOLIO_IMAGE, COURSE_VIDEO hoặc COURSE_PDF.", nullable = true)
        String assetType,
        @Schema(description = "Header FE phải gửi khi upload; chỉ chứa yêu cầu an toàn do backend công bố.", nullable = true)
        Map<String, String> requiredHeaders
) {
}
