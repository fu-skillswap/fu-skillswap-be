package com.fptu.exe.skillswap.modules.forum.service;

import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class ForumTextPolicy {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<\\s*/?\\s*[a-zA-Z][^>]*>");

    public String requirePlainText(String value, String fieldLabel) {
        String normalized = normalizeNullablePlainText(value, fieldLabel);
        if (normalized == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, fieldLabel + " không được để trống");
        }
        return normalized;
    }

    public String normalizeOptionalPlainText(String value, String fieldLabel) {
        return normalizeNullablePlainText(value, fieldLabel);
    }

    private String normalizeNullablePlainText(String value, String fieldLabel) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (HTML_TAG_PATTERN.matcher(trimmed).find()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, fieldLabel + " chỉ hỗ trợ plain text, không chấp nhận HTML");
        }
        return trimmed;
    }
}
