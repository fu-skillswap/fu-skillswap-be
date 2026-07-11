package com.fptu.exe.skillswap.modules.mail.template;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlEmailTemplateTest {

    @Test
    void render_shouldUseSkillSwapBrandedLayout() {
        String html = HtmlEmailTemplate.render(new HtmlEmailTemplate.Model(
                "Subject",
                "Preview",
                "Status",
                "Heading",
                "Summary",
                "Side label",
                "Side title",
                "Nguyen Van A",
                "Intro",
                HtmlEmailTemplate.detailRow("Label", HtmlEmailTemplate.escape("Value")),
                "Next step",
                "CTA",
                "https://skillswap.asia"
        ));

        assertTrue(html.contains("background:#f2f7fb"));
        assertTrue(html.contains("border-radius:28px"));
        assertTrue(html.contains("https://i.ibb.co/psBSNZt/logo.png"));
        assertTrue(html.contains("https://skillswap.asia"));
        assertTrue(html.contains("https://www.facebook.com/skillswap.fptedu"));
        assertTrue(html.contains("https://www.tiktok.com/@skillswap.fptu"));
        assertTrue(html.contains("Email tự động từ hệ thống"));
    }
}
