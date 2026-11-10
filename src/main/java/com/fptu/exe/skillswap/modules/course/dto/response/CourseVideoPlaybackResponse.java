package com.fptu.exe.skillswap.modules.course.dto.response;

import com.fptu.exe.skillswap.modules.course.domain.StorageProviderType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Thông tin phát video cho người học. playbackUrl hoặc embedUrl dùng để phát video trên FE.")
public class CourseVideoPlaybackResponse {
    @Schema(description = "ID tài liệu video.")
    private UUID materialId;

    @Schema(description = "Tên video hiển thị.")
    private String title;

    @Schema(description = "URL phát video tạm thời hoặc URL nhúng YouTube; FE đưa vào video player hoặc iframe.")
    private String playbackUrl;

    @Schema(description = "Loại nhà cung cấp lưu trữ (YOUTUBE, OBJECT_STORAGE, BUNNY_VIDEO).", example = "YOUTUBE")
    private StorageProviderType storageProviderType;

    @Schema(description = "Mã video 11 ký tự của YouTube nếu là video YouTube.", example = "dQw4w9WgXcQ", nullable = true)
    private String youtubeVideoId;

    @Schema(description = "URL xem video gốc trên YouTube nếu là video YouTube.", example = "https://www.youtube.com/watch?v=dQw4w9WgXcQ", nullable = true)
    private String youtubeUrl;

    @Schema(description = "URL nhúng YouTube trực tiếp cho thẻ iframe.", example = "https://www.youtube.com/embed/dQw4w9WgXcQ", nullable = true)
    private String embedUrl;

    @Schema(description = "URL ảnh thumbnail nếu có.", nullable = true)
    private String thumbnailUrl;

    @Schema(description = "Thời lượng video tính bằng giây.", nullable = true)
    private Integer durationSeconds;

    @Schema(description = "Thời điểm URL phát hết hạn theo UTC; với YouTube URL không hết hạn (null).", nullable = true)
    private Instant expiresAt;
}
