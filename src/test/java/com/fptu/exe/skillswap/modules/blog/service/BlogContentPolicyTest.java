package com.fptu.exe.skillswap.modules.blog.service;

import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BlogContentPolicyTest {

    private final BlogContentPolicy policy = new BlogContentPolicy();

    @Test
    void slugify_shouldStripVietnameseAccents() {
        assertEquals("lap-trinh-java-cho-nguoi-moi", policy.slugify(" Lập trình Java cho người mới "));
    }

    @Test
    void sha256Hex_blank_shouldReturnNull() {
        assertEquals(null, policy.sha256Hex("   "));
    }

    @Test
    void sha256Hex_content_shouldReturn64Chars() {
        assertEquals(64, policy.sha256Hex("hello").length());
    }

    @Test
    void validateMarkdown_shouldRejectDangerousPattern() {
        assertThrows(BaseException.class, () -> policy.validateMarkdown("[x](javascript:alert(1))"));
    }

    @Test
    void readingTime_shouldReturnAtLeastOneForContent() {
        assertEquals(1, policy.readingTimeMinutes("Short content"));
        assertNotNull(policy.cleanRequired(" value ", "field"));
    }
}
