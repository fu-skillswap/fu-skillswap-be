package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
public class GoogleCalendarDateTimeMapper {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of(DateTimeUtil.ZONE_HCM);

    public Map<String, String> toGoogleDateTime(LocalDateTime value) {
        ZonedDateTime zoned = value.atZone(BUSINESS_ZONE);
        return Map.of(
                "dateTime", zoned.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                "timeZone", BUSINESS_ZONE.getId()
        );
    }
}
