package com.fptu.exe.skillswap.shared.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class DateTimeUtil {
    public static final String DEFAULT_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String ZONE_HCM = "Asia/Ho_Chi_Minh";

    // Lấy thời gian hiện tại theo chuẩn múi giờ VN
    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of(ZONE_HCM));
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
