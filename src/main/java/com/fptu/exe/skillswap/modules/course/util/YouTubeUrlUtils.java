package com.fptu.exe.skillswap.modules.course.util;

import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class YouTubeUrlUtils {

    private static final Pattern YOUTUBE_PATTERN = Pattern.compile(
            "(?:https?:\\/\\/)?(?:www\\.|m\\.)?(?:youtube\\.com\\/(?:watch\\?.*?v=|embed\\/|v\\/|shorts\\/)|youtu\\.be\\/)([a-zA-Z0-9_-]{11})",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern RAW_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{11}$");

    private YouTubeUrlUtils() {
    }

    public static String extractVideoId(String input) {
        if (input == null || input.isBlank()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Link YouTube không được để trống");
        }
        String trimmed = input.trim();
        if (RAW_ID_PATTERN.matcher(trimmed).matches()) {
            return trimmed;
        }
        Matcher matcher = YOUTUBE_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new BaseException(
                ErrorCode.BAD_REQUEST,
                "Đường dẫn video YouTube không hợp lệ. Vui lòng cung cấp link dạng https://www.youtube.com/watch?v=... hoặc https://youtu.be/..."
        );
    }

    public static String buildCanonicalUrl(String videoId) {
        return "https://www.youtube.com/watch?v=" + videoId;
    }

    public static String buildEmbedUrl(String videoId) {
        return "https://www.youtube.com/embed/" + videoId;
    }

    public static String buildThumbnailUrl(String videoId) {
        return "https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg";
    }
}
