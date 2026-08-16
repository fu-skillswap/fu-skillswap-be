package com.fptu.exe.skillswap.infrastructure.bunny.webhook;

import com.fptu.exe.skillswap.infrastructure.bunny.config.BunnyStreamProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
@RequiredArgsConstructor
@Slf4j
public class BunnyWebhookVerifier {

    private final BunnyStreamProperties bunnyStreamProperties;

    /**
     * Kiểm tra chữ ký webhook từ Bunny.net.
     * Bunny.net dùng SHA-256 của payload nối với webhook secret.
     * Bunny dùng SHA256(payload + secret), không phải HMAC-SHA256.
     */
    public boolean verifySignature(String rawPayload, String signatureHeader) {
        if (rawPayload == null || signatureHeader == null) {
            return false;
        }

        String secret = bunnyStreamProperties.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("Bunny webhook secret is not configured");
            return false;
        }

        try {
            // Header Signature là SHA-256 của raw POST body nối với webhook secret.
            String dataToHash = rawPayload + secret;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(dataToHash.getBytes(StandardCharsets.UTF_8));
            
            // Đổi sang chuỗi hex.
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            String expectedSignature = hexString.toString().toLowerCase();
            return expectedSignature.equals(signatureHeader.toLowerCase());
        } catch (Exception e) {
            log.error("Failed to verify Bunny webhook signature", e);
            return false;
        }
    }
}
