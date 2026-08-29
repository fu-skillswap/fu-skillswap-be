package com.fptu.exe.skillswap.shared.time;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * Flexible deserializer for Instant fields.
 * Supports:
 * - ISO-8601 UTC Instant: "2026-08-30T11:16:00Z"
 * - ISO-8601 OffsetDateTime: "2026-08-30T18:16:00+07:00"
 * - Vietnam local datetime string without offset: "2026-08-30T18:16:00" or "2026-08-30 18:16:00" -> auto converted from Asia/Ho_Chi_Minh to UTC Instant.
 */
public class FlexibleInstantDeserializer extends JsonDeserializer<Instant> {

    private static final ZoneId VN_ZONE = TimeProvider.BUSINESS_ZONE;

    @Override
    public Instant deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String text = parser.getText();
        if (text == null || text.isBlank()) {
            return null;
        }
        text = text.trim();

        // 1. Try parse as standard UTC Instant (e.g. 2026-08-30T11:16:00Z)
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
        }

        // 2. Try parse as OffsetDateTime (e.g. 2026-08-30T18:16:00+07:00)
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException ignored) {
        }

        // 3. Try parse as Local Vietnam Time (e.g. 2026-08-30T18:16:00, 2026-08-30 18:16:00, 2026-08-30T18:16)
        try {
            String normalized = text.replace(" ", "T");
            LocalDateTime ldt = LocalDateTime.parse(normalized);
            return ldt.atZone(VN_ZONE).toInstant();
        } catch (DateTimeParseException ignored) {
        }

        throw new IllegalArgumentException("Không thể nhận diện định dạng thời gian: " + text);
    }
}
