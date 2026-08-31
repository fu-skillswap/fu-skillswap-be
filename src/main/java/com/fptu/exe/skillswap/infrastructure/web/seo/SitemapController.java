package com.fptu.exe.skillswap.infrastructure.web.seo;

import com.fptu.exe.skillswap.modules.blog.port.BlogShareQueryPort;
import com.fptu.exe.skillswap.modules.mentor.port.MentorShareQueryPort;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
@Tag(name = "SEO & Social Sharing")
public class SitemapController {

    private final BlogShareQueryPort blogShareQueryPort;
    private final MentorShareQueryPort mentorShareQueryPort;

    private static final String SITEMAP_START = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n";
    private static final String SITEMAP_END = "</urlset>";

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Get sitemap.xml", description = "Public XML sitemap for public mentor profiles and published public blog posts.")
    public ResponseEntity<String> getSitemap() {
        StringBuilder sb = new StringBuilder(SITEMAP_START);

        // Add static pages
        sb.append(createUrlTag("https://skillswap.asia", "1.0", "daily"));
        sb.append(createUrlTag("https://skillswap.asia/mentors", "0.9", "daily"));
        sb.append(createUrlTag("https://skillswap.asia/blog", "0.9", "daily"));

        // Add active mentors
        List<UUID> mentorIds = mentorShareQueryPort.findPublicMentorUserIds();
        
        for (UUID mentorId : mentorIds) {
            sb.append(createUrlTag("https://skillswap.asia/mentors/" + mentorId, "0.8", "weekly"));
        }

        // Add public published blogs
        List<String> blogSlugs = blogShareQueryPort.findPublicPublishedSlugs();

        for (String slug : blogSlugs) {
            sb.append(createUrlTag("https://skillswap.asia/blog/" + slug, "0.8", "weekly"));
        }

        sb.append(SITEMAP_END);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(12, TimeUnit.HOURS).cachePublic())
                .body(sb.toString());
    }

    private String createUrlTag(String url, String priority, String changefreq) {
        return "  <url>\n" +
               "    <loc>" + url + "</loc>\n" +
               "    <changefreq>" + changefreq + "</changefreq>\n" +
               "    <priority>" + priority + "</priority>\n" +
               "  </url>\n";
    }
}
