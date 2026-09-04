package com.fptu.exe.skillswap.modules.filestorage.dto.response;

import com.fptu.exe.skillswap.shared.dto.response.ProviderNeutralUploadMetadata;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Kết quả upload file. uploadUrl là URL tạm thời; objectKey là Internal field - FE không cần sử dụng và không nên tự tạo.")
public class PresignedUploadResponse {
    @Schema(description = "URL tạm thời để upload file trực tiếp. FE dùng ngay và không lưu làm URL cố định.")
    private String uploadUrl;
    @Schema(description = "URL public nếu flow cho phép truy cập public; có thể null với private upload.")
    private String publicUrl;
    @Schema(description = "Internal field - FE không cần sử dụng. Khóa lưu trữ nội bộ do backend tạo.")
    @Deprecated
    private String objectKey;
    @Schema(description = "Metadata upload trung lập với provider; FE ưu tiên dùng object này. objectKey cũ vẫn giữ để tương thích.")
    private ProviderNeutralUploadMetadata uploadMetadata;
}
