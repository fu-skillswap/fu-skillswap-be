package com.fptu.exe.skillswap.modules.course.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Thông tin phát video cho người học. playbackUrl và expiresAt là dữ liệu tạm thời do backend cấp.")
public class CourseVideoPlaybackResponse {
    @Schema(description = "ID tài liệu video.")
    private UUID materialId;
    @Schema(description = "Tên video hiển thị.")
    private String title;
    @Schema(description = "URL phát video tạm thời; FE không lưu làm URL cố định.")
    private String playbackUrl;
    @Schema(description = "URL ảnh thumbnail nếu có.", nullable = true)
    private String thumbnailUrl;
    @Schema(description = "Thời lượng video tính bằng giây.", nullable = true)
    private Integer durationSeconds;
    @Schema(description = "Thời điểm URL phát hết hạn theo UTC; sau đó FE cần gọi lại API.", nullable = true)
    private Instant expiresAt;
}
