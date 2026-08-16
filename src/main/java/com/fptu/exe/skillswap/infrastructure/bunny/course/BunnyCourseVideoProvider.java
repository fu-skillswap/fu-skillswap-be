package com.fptu.exe.skillswap.infrastructure.bunny.course;

import com.fptu.exe.skillswap.infrastructure.bunny.client.BunnyVideoClient;
import com.fptu.exe.skillswap.infrastructure.bunny.config.BunnyStreamProperties;
import com.fptu.exe.skillswap.modules.course.port.CourseVideoProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Adapter Bunny Stream cho hợp đồng video do module Course sở hữu. */
@Component
@RequiredArgsConstructor
public class BunnyCourseVideoProvider implements CourseVideoProvider {

    private final BunnyVideoClient bunnyVideoClient;
    private final BunnyStreamProperties bunnyStreamProperties;

    @Override
    public CreatedVideo createVideo(String title) {
        var created = bunnyVideoClient.createVideo(title);
        return new CreatedVideo(bunnyStreamProperties.getLibraryId(), created.getGuid());
    }

    @Override
    public void deleteVideo(String videoId) {
        bunnyVideoClient.deleteVideo(videoId);
    }

    @Override
    public String generateDirectUploadSignature(String videoId, long expirationTimestamp) {
        return bunnyVideoClient.generateDirectUploadSignature(videoId, expirationTimestamp);
    }

    @Override
    public String generateSignedPlaybackUrl(String videoId, long ttlSeconds, String clientIp) {
        return bunnyVideoClient.generateSignedPlaybackUrl(videoId, ttlSeconds, clientIp);
    }
}
