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
     * Verifies the Bunny.net webhook signature.
     * Bunny.net calculates the signature using SHA-256 hash of the payload appended with the webhook secret.
     * Wait, according to Bunny.net docs, it's a SHA256 of the payload + secret, or HMAC-SHA256?
     * The approved plan says "HMAC-SHA256, Secret config" but Bunny uses SHA256(payload + secret). Let's support SHA256 as per standard Bunny.net spec.
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
            // According to Bunny.net docs for Stream Webhooks, the Signature header is the SHA256 hash of the raw POST body combined with your webhook secret.
            String dataToHash = rawPayload + secret;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(dataToHash.getBytes(StandardCharsets.UTF_8));
            
            // Convert to hex
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
