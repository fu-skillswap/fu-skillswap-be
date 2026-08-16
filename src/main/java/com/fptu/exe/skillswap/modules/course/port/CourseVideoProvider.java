package com.fptu.exe.skillswap.modules.course.port;

/**
 * Course-owned video capability. Provider-specific SDK types must not cross this boundary.
 */
public interface CourseVideoProvider {

    record CreatedVideo(String libraryId, String videoId) {
    }

    CreatedVideo createVideo(String title);

    void deleteVideo(String videoId);

    String generateDirectUploadSignature(String videoId, long expirationTimestamp);

    String generateSignedPlaybackUrl(String videoId, long ttlSeconds, String clientIp);
}
