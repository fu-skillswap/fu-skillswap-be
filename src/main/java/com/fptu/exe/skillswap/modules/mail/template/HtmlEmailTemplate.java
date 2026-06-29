package com.fptu.exe.skillswap.modules.mail.template;

public final class HtmlEmailTemplate {

    public static final String PLATFORM_URL = "https://skillswap.asia";

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
                <body style="margin:0;padding:0;background:#f2f7fb;font-family:sans-serif;color:#102a43;">
                  <div style="display:none;max-height:0;overflow:hidden;color:#f2f7fb;">%s</div>
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f2f7fb;padding:28px 12px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="640" cellspacing="0" cellpadding="0" style="max-width:640px;width:100%%;background:#ffffff;border-radius:28px;overflow:hidden;border:1px solid #d4e3ef;">
                          <tr>
                            <td style="padding:24px 34px;background:#f8fbff;text-align:center;border-bottom:1px solid #dbe8f3;">
                              <div style="font-size:34px;line-height:1;font-weight:800;color:#0b3a67;letter-spacing:-1px;">SkillSwap</div>
                              <div style="margin-top:8px;font-size:13px;color:#5d7083;">Nền tảng mentoring và trao đổi kỹ năng FPTU</div>
                            </td>
                          </tr>
                          <tr>
                            <td style="background:#d7eaf8;padding:0;">
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0">
                                <tr>
                                  <td style="padding:34px 30px 30px 34px;width:56%%;vertical-align:top;">
                                    <div style="display:inline-block;padding:7px 12px;border-radius:999px;background:#ffffff;color:#0b3a67;font-size:12px;font-weight:700;">%s</div>
                                    <h1 style="margin:18px 0 0;font-size:30px;line-height:1.15;color:#082f57;letter-spacing:-0.6px;">%s</h1>
                                    <p style="margin:14px 0 0;font-size:15px;line-height:1.6;color:#26465f;">%s</p>
                                  </td>
                                  <td style="padding:28px 30px 28px 0;vertical-align:middle;">
                                    <div style="background:#ffffff;border-radius:24px;padding:18px;border:1px solid #b9d6ea;box-shadow:0 10px 24px rgba(11,58,103,0.16);">
                                      <div style="height:12px;width:86px;background:#0b3a67;border-radius:999px;margin-bottom:14px;"></div>
                                      <div style="padding:12px;border-radius:16px;background:#f6faff;border:1px solid #dbe8f3;margin-bottom:10px;">
                                        <div style="font-size:12px;color:#5d7083;">%s</div>
                                        <div style="font-size:15px;font-weight:700;color:#102a43;">%s</div>
                                      </div>
                                      <table role="presentation" cellspacing="0" cellpadding="0" style="width:100%%;">
                                        <tr>
                                          <td style="padding-right:8px;"><span style="display:block;width:54px;height:42px;border-radius:14px;background:#b9d6ea;"></span></td>
                                          <td style="padding-right:8px;"><span style="display:block;width:54px;height:42px;border-radius:14px;background:#7fb3d5;"></span></td>
                                          <td><span style="display:block;width:54px;height:42px;border-radius:14px;background:#0b3a67;"></span></td>
                                        </tr>
                                      </table>
                                    </div>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:34px;">
                              <p style="margin:0 0 14px;font-size:17px;line-height:1.6;">Xin chào <strong>%s</strong>,</p>
                              <p style="margin:0 0 24px;font-size:15px;line-height:1.7;color:#314b61;">%s</p>
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="border:1px solid #d4e3ef;border-radius:20px;overflow:hidden;margin-bottom:22px;">
                                %s
                              </table>
                              <div style="background:#eef7ff;border:1px solid #b9d6ea;border-radius:18px;padding:18px 20px;margin:0 0 24px;">
                                <div style="font-size:13px;font-weight:800;color:#0b3a67;text-transform:uppercase;letter-spacing:.4px;">Bước tiếp theo</div>
                                <div style="margin-top:8px;font-size:15px;line-height:1.6;color:#173b59;">%s</div>
                              </div>
                              <div style="text-align:center;margin:30px 0 18px;">
                                <a href="%s" style="display:inline-block;background:#0b3a67;color:#ffffff;text-decoration:none;font-weight:800;border-radius:999px;padding:14px 28px;font-size:15px;">%s</a>
                              </div>
                              <p style="margin:18px 0 0;font-size:13px;line-height:1.6;color:#697f91;text-align:center;">Website: <a href="%s" style="color:#0b3a67;">%s</a></p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:22px 34px;background:#062b4f;color:#d7eaf8;text-align:center;">
                              <div style="font-size:14px;font-weight:800;color:#ffffff;">SkillSwap</div>
                              <div style="margin-top:8px;font-size:12px;line-height:1.6;">Email tự động từ hệ thống. Vui lòng không trả lời trực tiếp email này.</div>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """;

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
                escape(model.ctaLabel()),
                escapedCtaUrl,
                escapedCtaUrl
        );
    }

    public static String detailRow(String label, String value) {
        return """
                <tr>
                  <td style="padding:13px 16px;background:#f6faff;border-bottom:1px solid #d4e3ef;width:34%%;font-size:13px;font-weight:800;color:#526a80;">%s</td>
                  <td style="padding:13px 16px;border-bottom:1px solid #d4e3ef;font-size:14px;line-height:1.55;color:#102a43;">%s</td>
                </tr>
                """.formatted(escape(label), value == null ? "" : value);
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
        return "<a href=\"" + escaped + "\" style=\"color:#0b3a67;text-decoration:none;font-weight:700;\">" + escaped + "</a>";
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
