package com.fptu.exe.skillswap.infrastructure.bunny.client;

import com.fptu.exe.skillswap.infrastructure.bunny.dto.BunnyCreateVideoResponse;

public interface BunnyVideoClient {

    /**
     * Tạo video mới trong thư viện Bunny.net Stream.
     * @param title Tiêu đề video.
     * @return Kết quả có ID video (GUID).
     */
    BunnyCreateVideoResponse createVideo(String title);

    /**
     * Xóa video khỏi thư viện Bunny.net Stream.
     * @param videoId GUID của video.
     */
    void deleteVideo(String videoId);

    /**
     * Tạo chữ ký để client tải video thẳng lên Bunny.net.
     * @param videoId GUID của video.
     * @param expirationTimestamp UNIX timestamp khi chữ ký hết hạn.
     * @return Chữ ký băm SHA-256.
     */
    String generateDirectUploadSignature(String videoId, long expirationTimestamp);

    /**
     * Tạo URL xem video có chữ ký theo chuẩn Bunny.net Token Authentication.
     * Có hỗ trợ ràng buộc token với IP.
     * @param videoId GUID của video.
     * @param ttlSeconds Thời gian sống của token, tính bằng giây.
     * @param clientIp IP của client cần ràng buộc với token.
     * @return URL xem video đã ký.
     */
    String generateSignedPlaybackUrl(String videoId, long ttlSeconds, String clientIp);
}
