package com.fptu.exe.skillswap.modules.blog.service;

import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class BlogContentPolicy {

    public String slugify(String value) {
        String source = hasText(value) ? value.trim() : "blog-post";
        String normalized = Normalizer.normalize(source, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return hasText(normalized) ? normalized : "blog-post";
    }

    public String cleanRequired(String value, String label) {
        if (!hasText(value)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, label + " không được để trống");
        }
        return value.trim();
    }

    public String cleanNullable(String value) {
        return hasText(value) ? value.trim() : null;
    }

    public void validateMarkdown(String markdown) {
        if (!hasText(markdown)) {
            return;
        }
        // Raw HTML makes safety dependent on whichever renderer the client happens to use.
        // Keep authored content to Markdown and allow code samples inside fenced code blocks.
        String withoutCodeBlocks = markdown.replaceAll("(?s)```.*?```", "");
        if (withoutCodeBlocks.contains("<") || withoutCodeBlocks.contains(">")
                || withoutCodeBlocks.matches("(?s).*\\]\\s*\\(\\s*(?:javascript|vbscript|data)\\s*:.*")) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Nội dung markdown chứa HTML hoặc liên kết không an toàn");
        }
    }

    public String sha256Hex(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.trim().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Không thể tạo content hash", ex);
        }
    }

    public int readingTimeMinutes(String markdown) {
        if (!hasText(markdown)) {
            return 0;
        }
        String plain = markdown.replaceAll("`{1,3}[^`]*`{1,3}", " ")
                .replaceAll("[#>*_\\-\\[\\]()!]", " ");
        int words = plain.trim().isEmpty() ? 0 : plain.trim().split("\\s+").length;
        return Math.max(1, (int) Math.ceil(words / 220.0));
    }

    public boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
