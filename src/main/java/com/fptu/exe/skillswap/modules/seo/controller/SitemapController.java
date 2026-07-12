package com.fptu.exe.skillswap.modules.seo.controller;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
public class SitemapController {

    private final EntityManager entityManager;

    private static final String SITEMAP_START = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n";
    private static final String SITEMAP_END = "</urlset>";

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> getSitemap() {
        StringBuilder sb = new StringBuilder(SITEMAP_START);

        // Add static pages
        sb.append(createUrlTag("https://skillswap.asia", "1.0", "daily"));
        sb.append(createUrlTag("https://skillswap.asia/mentors", "0.9", "daily"));
        sb.append(createUrlTag("https://skillswap.asia/blog", "0.9", "daily"));

        // Add active mentors
        List<UUID> mentorIds = entityManager.createQuery(
                "SELECT m.userId FROM MentorProfile m WHERE m.isAvailable = true", UUID.class)
                .getResultList();
        
        for (UUID mentorId : mentorIds) {
            sb.append(createUrlTag("https://skillswap.asia/mentors/" + mentorId, "0.8", "weekly"));
        }

        // Add public published blogs
        List<String> blogSlugs = entityManager.createQuery(
                "SELECT b.slug FROM BlogPost b WHERE b.visibility = 'PUBLIC' AND b.status = 'PUBLISHED'", String.class)
                .getResultList();

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
