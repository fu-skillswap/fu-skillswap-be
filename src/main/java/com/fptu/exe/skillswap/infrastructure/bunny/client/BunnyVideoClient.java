package com.fptu.exe.skillswap.infrastructure.bunny.client;

import com.fptu.exe.skillswap.infrastructure.bunny.dto.BunnyCreateVideoResponse;

public interface BunnyVideoClient {

    /**
     * Creates a new video object in Bunny.net stream library.
     * @param title Title of the video.
     * @return Response containing the video ID (GUID).
     */
    BunnyCreateVideoResponse createVideo(String title);

    /**
     * Deletes a video from Bunny.net stream library.
     * @param videoId The GUID of the video.
     */
    void deleteVideo(String videoId);

    /**
     * Generates a direct upload signature for the client to upload video directly to Bunny.net.
     * @param videoId The GUID of the video.
     * @param expirationTimestamp The UNIX timestamp when the signature expires.
     * @return The SHA256 hashed signature.
     */
    String generateDirectUploadSignature(String videoId, long expirationTimestamp);

    /**
     * Generates a signed playback URL using Bunny.net Token Authentication specification.
     * Encapsulates the HMAC/SHA256 signing logic, including IP binding.
     * @param videoId The GUID of the video.
     * @param ttlSeconds Time-to-live for the token in seconds.
     * @param clientIp The IP address of the client to bind the token to.
     * @return The signed playback URL.
     */
    String generateSignedPlaybackUrl(String videoId, long ttlSeconds, String clientIp);
}
