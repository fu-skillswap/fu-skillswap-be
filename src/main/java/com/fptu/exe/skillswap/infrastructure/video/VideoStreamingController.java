package com.fptu.exe.skillswap.infrastructure.video;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Internal/System endpoint called by the VPS reverse proxy, never by FE directly. */
@RestController
@RequestMapping("/api/internal/video-streaming")
@RequiredArgsConstructor
@Tag(name = "Internal/System - Video streaming", description = "Internal/System - không dùng cho FE. Nginx gọi endpoint này để xin source URL tạm thời từ R2.")
public class VideoStreamingController {
    private final VideoStreamingAuthorizationService authorizationService;

    @Operation(summary = "Internal - authorize video stream", description = "Internal/System - không dùng cho FE. Nginx gửi asset ID và playback token; backend trả source URL R2 qua response header rồi Nginx proxy dữ liệu với HTTP Range.")
    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(
            @RequestHeader("X-Video-Asset-Id") UUID assetId,
            @RequestHeader("X-Video-Token") String token) {
        VideoStreamingAuthorizationService.StreamGrant grant = authorizationService.authorize(assetId, token);
        return ResponseEntity.noContent()
                .header("X-Video-Source-Url", grant.sourceUrl())
                .header("X-Video-Source-Host", grant.sourceHost())
                .header("X-Video-Content-Type", grant.contentType())
                .build();
    }
}
