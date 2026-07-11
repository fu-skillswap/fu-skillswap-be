package com.fptu.exe.skillswap.modules.identity.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoogleCalendarDateTimeMapperTest {

    private final GoogleCalendarDateTimeMapper mapper = new GoogleCalendarDateTimeMapper();

    @Test
    void shouldMapToRfc3339WithAsiaHoChiMinhOffset() {
        Map<String, String> value = mapper.toGoogleDateTime(LocalDateTime.of(2026, 7, 8, 16, 0));

        assertEquals("Asia/Ho_Chi_Minh", value.get("timeZone"));
        assertEquals("2026-07-08T16:00:00+07:00", value.get("dateTime"));
    }
}
