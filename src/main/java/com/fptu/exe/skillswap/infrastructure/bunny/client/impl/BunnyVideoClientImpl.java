package com.fptu.exe.skillswap.infrastructure.bunny.client.impl;

import com.fptu.exe.skillswap.infrastructure.bunny.client.BunnyVideoClient;
import com.fptu.exe.skillswap.infrastructure.bunny.config.BunnyStreamProperties;
import com.fptu.exe.skillswap.infrastructure.bunny.dto.BunnyCreateVideoRequest;
import com.fptu.exe.skillswap.infrastructure.bunny.dto.BunnyCreateVideoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;

@Service
@Slf4j
public class BunnyVideoClientImpl implements BunnyVideoClient {

    private final BunnyStreamProperties properties;
    private final RestTemplate restTemplate;

    @Autowired
    public BunnyVideoClientImpl(BunnyStreamProperties properties, RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public BunnyCreateVideoResponse createVideo(String title) {
        String url = String.format("%s/library/%s/videos", properties.getApiUrl(), properties.getLibraryId());
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("AccessKey", properties.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("accept", "application/json");

        BunnyCreateVideoRequest request = new BunnyCreateVideoRequest(title);
        HttpEntity<BunnyCreateVideoRequest> entity = new HttpEntity<>(request, headers);

        try {
            return restTemplate.postForObject(url, entity, BunnyCreateVideoResponse.class);
        } catch (Exception e) {
            log.error("Failed to create video on Bunny.net", e);
            throw new RuntimeException("Error communicating with video provider", e);
        }
    }

    @Override
    public void deleteVideo(String videoId) {
        String url = String.format("%s/library/%s/videos/%s", properties.getApiUrl(), properties.getLibraryId(), videoId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("AccessKey", properties.getApiKey());
        headers.set("accept", "application/json");

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            restTemplate.exchange(url, HttpMethod.DELETE, entity, Void.class);
        } catch (HttpClientErrorException.NotFound ignored) {
            // Deletion is idempotent: a previously removed Bunny object is already in the desired state.
            log.info("Bunny video {} was already deleted", videoId);
        } catch (Exception e) {
            log.error("Failed to delete video on Bunny.net for videoId: {}", videoId, e);
            throw new RuntimeException("Error deleting video from provider", e);
        }
    }

    @Override
    public String generateDirectUploadSignature(String videoId, long expirationTimestamp) {
        // Bunny API for uploading requires SHA256 of (libraryId + api_key + expirationTime + videoId)
        // according to docs, signature is for tus/direct upload.
        // We will generate the signature required for their API.
        // The standard format: sha256(LibraryId + ApiKey + ExpirationTime + VideoId)
        String rawData = properties.getLibraryId() + properties.getApiKey() + expirationTimestamp + videoId;
        return hashSha256(rawData);
    }

    @Override
    public String generateSignedPlaybackUrl(String videoId, long ttlSeconds, String clientIp) {
        long expiresAt = Instant.now().plusSeconds(ttlSeconds).getEpochSecond();
        // Bunny Token Auth for embed with IP validation: token = sha256(securityKey + videoId + expiresAt + clientIp)
        String rawData = properties.getTokenAuthKey() + videoId + expiresAt + (clientIp != null ? clientIp : "");
        String token = hashSha256(rawData);

        return String.format("https://%s/embed/%s/%s?token=%s&expires=%d",
                properties.getCdnHostname(),
                properties.getLibraryId(),
                videoId,
                token,
                expiresAt);
    }

    private String hashSha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    private String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
