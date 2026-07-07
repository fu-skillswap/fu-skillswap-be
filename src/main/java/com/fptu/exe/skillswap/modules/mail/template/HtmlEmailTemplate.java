package com.fptu.exe.skillswap.modules.mail.template;

public final class HtmlEmailTemplate {

    public static final String PLATFORM_URL = "https://skillswap.asia";
    private static final String FONT_STACK = "'Segoe UI', Arial, Helvetica, sans-serif";

    private HtmlEmailTemplate() {
    }

    public static String render(Model model) {
        String html = """
                <!doctype html>
                <html lang="vi">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background:#f2f7fb;font-family:__FONT_STACK__;color:#102a43;">
                  <div style="display:none;max-height:0;overflow:hidden;color:#f2f7fb;font-family:__FONT_STACK__;">%s</div>
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f2f7fb;padding:28px 12px;font-family:__FONT_STACK__;">
                    <tr>
                      <td align="center" style="font-family:__FONT_STACK__;">
                        <table role="presentation" width="640" cellspacing="0" cellpadding="0" style="max-width:640px;width:100%%;background:#ffffff;border-radius:28px;overflow:hidden;border:1px solid #d4e3ef;font-family:__FONT_STACK__;">
                          <tr>
                            <td style="background:#d7eaf8;padding:0;font-family:__FONT_STACK__;">
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="font-family:__FONT_STACK__;">
                                <tr>
                                  <td style="padding:34px 10px 30px 34px;width:50%%;vertical-align:top;font-family:__FONT_STACK__;">
                                    <div style="display:inline-block;padding:7px 12px;border-radius:999px;background:#ffffff;color:#0b3a67;font-size:12px;font-weight:700;font-family:__FONT_STACK__;">%s</div>
                                    <h1 style="margin:18px 0 0;font-size:30px;line-height:1.15;color:#082f57;letter-spacing:-0.6px;font-family:__FONT_STACK__;">%s</h1>
                                    <p style="margin:14px 0 0;font-size:15px;line-height:1.6;color:#26465f;font-family:__FONT_STACK__;">%s</p>
                                  </td>
                                  <td style="width:50%%;padding:28px 10px;vertical-align:middle;text-align:center;font-family:__FONT_STACK__;">
                                    <a href="https://skillswap.asia" target="_blank" style="text-decoration:none;display:inline-block;">
                                      <img src="https://i.ibb.co/psBSNZt/logo.png" alt="SkillSwap Logo" style="display:block;max-width:140px;border:0;margin:0 auto;">
                                    </a>
                                    <div style="margin-top:14px;font-size:12px;color:#5d7083;font-family:__FONT_STACK__;">%s</div>
                                    <div style="margin-top:4px;font-size:15px;font-weight:700;color:#102a43;font-family:__FONT_STACK__;">%s</div>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:34px;font-family:__FONT_STACK__;">
                              <p style="margin:0 0 14px;font-size:17px;line-height:1.6;font-family:__FONT_STACK__;">Xin chào <strong>%s</strong>,</p>
                              <p style="margin:0 0 24px;font-size:15px;line-height:1.7;color:#314b61;font-family:__FONT_STACK__;">%s</p>
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="border:1px solid #d4e3ef;border-radius:20px;overflow:hidden;margin-bottom:22px;font-family:__FONT_STACK__;">
                                %s
                              </table>
                              <div style="background:#eef7ff;border:1px solid #b9d6ea;border-radius:18px;padding:18px 20px;margin:0 0 24px;">
                                <div style="font-size:13px;font-weight:800;color:#0b3a67;text-transform:uppercase;letter-spacing:.4px;font-family:__FONT_STACK__;">Bước tiếp theo</div>
                                <div style="margin-top:8px;font-size:15px;line-height:1.6;color:#173b59;font-family:__FONT_STACK__;">%s</div>
                              </div>
                              <div style="text-align:center;margin:30px 0 24px;">
                                <a href="%s" style="display:inline-block;background:#0b3a67;color:#ffffff;text-decoration:none;font-weight:800;border-radius:999px;padding:14px 28px;font-size:15px;font-family:__FONT_STACK__;">%s</a>
                              </div>
                              <hr style="border:0;border-top:1px solid #d4e3ef;margin:30px 0 20px;">
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:20px 34px;background:#0f4c81;color:#d7eaf8;text-align:center;font-family:__FONT_STACK__;">
                              <div style="font-size:15px;line-height:1.6;color:#ffffff;font-family:__FONT_STACK__;">
                                Best regards,<br>
                                <strong>SkillSwap Team</strong>
                              </div>
                              
                              <div style="margin-top:20px;text-align:center;font-family:__FONT_STACK__;">
                                <a href="https://skillswap.asia" style="display:inline-block;margin:0 10px;text-decoration:none;vertical-align:middle;" target="_blank">
                                  <img src="https://img.icons8.com/color/48/domain--v1.png" width="24" height="24" alt="Website" style="display:block;border:0;">
                                </a>
                                <a href="https://www.facebook.com/skillswap.fptedu" style="display:inline-block;margin:0 10px;text-decoration:none;vertical-align:middle;" target="_blank">
                                  <img src="https://img.icons8.com/color/48/facebook-new.png" width="24" height="24" alt="Facebook" style="display:block;border:0;">
                                </a>
                                <a href="https://www.tiktok.com/@skillswap.fptu?_r=1&_t=ZS-97dweZeVLIy" style="display:inline-block;margin:0 10px;text-decoration:none;vertical-align:middle;" target="_blank">
                                  <img src="https://img.icons8.com/color/48/tiktok.png" width="24" height="24" alt="TikTok" style="display:block;border:0;">
                                </a>
                              </div>
                              
                              <div style="margin-top:14px;font-size:12px;line-height:1.6;color:#a8c6df;font-family:__FONT_STACK__;">Email tự động từ hệ thống. Vui lòng không trả lời trực tiếp email này.</div>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """;

        html = html.replace("__FONT_STACK__", FONT_STACK);

        String ctaUrl = defaultText(model.ctaUrl(), PLATFORM_URL);
        String escapedCtaUrl = escape(ctaUrl);
        return String.format(html,
                escape(model.subject()),
                escape(model.previewText()),
                escape(model.statusLabel()),
                escape(model.heading()),
                escape(model.summary()),
                escape(defaultText(model.sideLabel(), "SkillSwap")),
                escape(defaultText(model.sideTitle(), model.heading())),
                escape(defaultText(model.recipientName(), "bạn")),
                escape(model.intro()),
                model.detailRowsHtml() == null ? "" : model.detailRowsHtml(),
                escape(model.nextStep()),
                escapedCtaUrl,
                escape(model.ctaLabel())
        );
    }

    public static String detailRow(String label, String value) {
        return """
                <tr>
                  <td style="padding:13px 16px;background:#f6faff;border-bottom:1px solid #d4e3ef;width:34%%;font-size:13px;font-weight:800;color:#526a80;font-family:%s;">%s</td>
                  <td style="padding:13px 16px;border-bottom:1px solid #d4e3ef;font-size:14px;line-height:1.55;color:#102a43;font-family:%s;">%s</td>
                </tr>
                """.formatted(FONT_STACK, escape(label), FONT_STACK, value == null ? "" : value);
    }

    public static String safeLink(String url) {
        String trimmed = trimToNull(url);
        if (trimmed == null) {
            return "";
        }
        String escaped = escape(trimmed);
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return escaped;
        }
        return "<a href=\"" + escaped + "\" style=\"color:#0b3a67;text-decoration:none;font-weight:700;font-family:" + FONT_STACK + ";\">" + escaped + "</a>";
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public static String defaultText(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    public static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record Model(
            String subject,
            String previewText,
            String statusLabel,
            String heading,
            String summary,
            String sideLabel,
            String sideTitle,
            String recipientName,
            String intro,
            String detailRowsHtml,
            String nextStep,
            String ctaLabel,
            String ctaUrl
    ) {
    }
}
