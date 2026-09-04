package com.fptu.exe.skillswap.modules.course.dto.response;

import com.fptu.exe.skillswap.shared.dto.response.ProviderNeutralUploadMetadata;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@Schema(description = "Thông tin để FE tải video khóa học lên trực tiếp. Các giá trị do backend tạo chỉ dùng cho lần upload hiện tại và có thể hết hạn.")
public class CourseVideoUploadInitResponse {
    @Schema(description = "ID của tài liệu video vừa được tạo.", example = "019f1234-aaaa-bbbb-cccc-1234567890ab")
    private UUID materialId;
    @Schema(description = "Internal field - FE không cần sử dụng. ID thư viện video của Bunny.", example = "123456")
    @Deprecated
    private String bunnyLibraryId;
    @Schema(description = "Internal field - FE không cần sử dụng. ID video do Bunny tạo.", example = "019f2234-aaaa-bbbb-cccc-1234567890ab")
    @Deprecated
    private String bunnyVideoId;
    @Schema(description = "URL upload tạm thời. FE dùng ngay cho lần upload này, không lưu làm URL cố định và không tự sửa giá trị.", example = "https://video-upload.example/upload")
    private String uploadUrl;
    @Schema(description = "Chữ ký upload tạm thời do backend tạo. FE gửi nguyên giá trị khi upload; giá trị có thể hết hạn.", example = "temporary-signature")
    private String authorizationSignature;
    @Schema(description = "Thời điểm hết hạn của quyền upload, biểu diễn bằng Unix timestamp.", example = "1780000000")
    private long expirationTimestamp;
    @Schema(description = "Metadata upload trung lập với provider; FE ưu tiên dùng object này. Các field Bunny cũ vẫn giữ để tương thích.")
    private ProviderNeutralUploadMetadata uploadMetadata;
}
