package com.fptu.exe.skillswap.modules.course.controller;

import com.fptu.exe.skillswap.infrastructure.bunny.dto.BunnyWebhookPayload;
import com.fptu.exe.skillswap.modules.course.domain.BunnyWebhookEvent;
import com.fptu.exe.skillswap.modules.course.dto.CourseVideoWebhook;
import com.fptu.exe.skillswap.infrastructure.bunny.webhook.BunnyWebhookVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.modules.course.service.CourseVaultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;

@RestController
@RequestMapping("/api/webhooks/bunny")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Internal/System", description = "Không dùng cho FE. Provider gọi callback vào các endpoint này.")
public class BunnyWebhookController {

    private final CourseVaultService courseVaultService;
    private final BunnyWebhookVerifier webhookVerifier;
    private final ObjectMapper objectMapper;

    @Operation(summary = "Nhận callback Bunny cho trạng thái xử lý video", description = "Internal/System - không dùng cho FE. Bunny gọi callback này; FE chỉ xem trạng thái video qua API Course. Signature sai trả 401, payload không đọc được trả 400.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Callback đã được tiếp nhận"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Payload provider không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Signature callback không hợp lệ")
    })
    @PostMapping("/video-events")
    public ResponseEntity<Void> handleBunnyWebhook(
            @RequestHeader(value = "Signature", required = false) String signatureHeader,
            @RequestBody String rawPayload) {
        
        if (!webhookVerifier.verifySignature(rawPayload, signatureHeader)) {
            log.warn("Invalid Bunny Webhook Signature");
            return ResponseEntity.status(401).build();
        }

        try {
            BunnyWebhookPayload payload = objectMapper.readValue(rawPayload, BunnyWebhookPayload.class);
            log.info("Received Bunny Webhook event: videoId={}, eventType={}", payload.getVideoGuid(), payload.getStatus());

            // Use SHA256 of raw payload as surrogate unique external event ID
            String externalEventId = generatePayloadHash(rawPayload);

            // 1. Save Event Audit Log (PENDING) idempotently
            courseVaultService.saveWebhookAuditLog(signatureHeader, externalEventId,
                    new CourseVideoWebhook(payload.getVideoGuid(), payload.getVideoLibraryId(), payload.getStatus()));

            // 2. Process asynchronously (handled by DB worker BunnyWebhookRetryScheduler)
            // DO NOT process synchronously here

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to parse Bunny Webhook payload", e);
            return ResponseEntity.badRequest().build();
        }
    }

    private String generatePayloadHash(String payload) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return java.util.UUID.randomUUID().toString();
        }
    }
}
