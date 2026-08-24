package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
public class GoogleCalendarDateTimeMapper {

    private static final ZoneId BUSINESS_ZONE = TimeProvider.BUSINESS_ZONE;

    public Map<String, String> toGoogleDateTime(Instant instant) {
        if (instant == null) {
            return Map.of();
        }
        ZonedDateTime zoned = instant.atZone(BUSINESS_ZONE);
        return Map.of(
                "dateTime", zoned.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                "timeZone", BUSINESS_ZONE.getId()
        );
    }

    public Map<String, String> toGoogleDateTime(OffsetDateTime offsetDateTime) {
        if (offsetDateTime == null) {
            return Map.of();
        }
        ZonedDateTime zoned = offsetDateTime.atZoneSameInstant(BUSINESS_ZONE);
        return Map.of(
                "dateTime", zoned.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                "timeZone", BUSINESS_ZONE.getId()
        );
    }

    public Map<String, String> toGoogleDateTime(LocalDateTime value) {
        if (value == null) {
            return Map.of();
        }
        ZonedDateTime zoned = value.atZone(BUSINESS_ZONE);
        return Map.of(
                "dateTime", zoned.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                "timeZone", BUSINESS_ZONE.getId()
        );
    }

    public Instant parseGoogleDateTime(String dateTimeString) {
        if (dateTimeString == null || dateTimeString.isBlank()) {
            return null;
        }
        return DateTimeFormatter.ISO_DATE_TIME.parse(dateTimeString, Instant::from);
    }
}
