package com.fptu.exe.skillswap.shared.util;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * @deprecated Legacy static time facade. New code in booking, payment, and subsequent
 *             slices must inject {@link com.fptu.exe.skillswap.shared.time.TimeProvider} or
 *             {@link Clock} directly instead of calling static DateTimeUtil.
 *             This class is maintained solely for JPA PrePersist/PreUpdate entity listeners
 *             and legacy un-migrated modules.
 */
@Deprecated(since = "2.0", forRemoval = false)
public class DateTimeUtil {
    public static final String DEFAULT_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String ZONE_HCM = "Asia/Ho_Chi_Minh";

    private static final ZoneId BUSINESS_ZONE = ZoneId.of(ZONE_HCM);
    private static volatile Clock clock = Clock.systemUTC();

    public static void setClock(Clock customClock) {
        clock = customClock;
    }

    public static Clock getClock() {
        return clock;
    }

    // Lấy thời gian hiện tại theo chuẩn múi giờ VN
    public static LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), BUSINESS_ZONE);
    }

    public static Instant instantNow() {
        return clock.instant();
    }

    public static OffsetDateTime offsetNow() {
        return now().atZone(BUSINESS_ZONE).toOffsetDateTime();
    }

    public static String format(LocalDateTime dateTime) {
        if (dateTime == null)
            return null;
        return dateTime.format(DateTimeFormatter.ofPattern(DEFAULT_FORMAT));
    }

    public static String format(LocalDateTime dateTime, String pattern) {
        if (dateTime == null)
            return null;
        return dateTime.format(DateTimeFormatter.ofPattern(pattern));
    }
}
