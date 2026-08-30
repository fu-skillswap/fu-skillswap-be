package com.fptu.exe.skillswap.modules.seo.controller;

import com.fptu.exe.skillswap.modules.blog.dto.BlogPostReaderDetailResponse;
import com.fptu.exe.skillswap.modules.blog.port.BlogQueryPort;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorDiscoveryDetailResponse;
import com.fptu.exe.skillswap.modules.mentor.port.MentorQueryPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/share")
@RequiredArgsConstructor
@Tag(name = "SEO & Social Sharing", description = "Public endpoints for Open Graph link previews and redirection")
public class SocialShareController {

    private final BlogQueryPort blogQueryPort;
    private final MentorQueryPort mentorQueryPort;

    private static final List<String> SOCIAL_BOT_USER_AGENTS = List.of(
            "facebookexternalhit", "twitterbot", "linkedinbot", "googlebot",
            "bingbot", "slackbot", "vkshare", "whatsapp", "telegrambot", "discordbot"
    );

    @GetMapping(value = "/blog/{slug}")
    @Operation(summary = "Share blog post", description = "Returns OG HTML for bots, or 302 redirect for humans")
    public ResponseEntity<String> shareBlog(
            @PathVariable String slug,
            @RequestHeader(value = HttpHeaders.USER_AGENT, defaultValue = "") String userAgent,
            HttpServletRequest request
    ) {
        String frontendUrl = "https://skillswap.asia/blog/" + slug;
        
        if (!isSocialBot(userAgent)) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(frontendUrl))
                    .build();
        }

        try {
            BlogPostReaderDetailResponse post = blogQueryPort.getBySlug(slug);
            String html = generateOgHtml(
                    post.title() != null ? post.title() : "SkillSwap Blog",
                    post.excerpt() != null ? post.excerpt() : "Đọc bài viết trên SkillSwap",
                    post.ogImageUrl() != null ? post.ogImageUrl() : post.coverImageUrl(),
                    frontendUrl
            );
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
        } catch (Exception e) {
            log.warn("Error serving OG tags for blog slug: {}", slug, e);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(frontendUrl))
                    .build();
        }
    }

    @GetMapping(value = "/mentor/{id}")
    @Operation(summary = "Share mentor profile", description = "Returns OG HTML for bots, or 302 redirect for humans")
    public ResponseEntity<String> shareMentor(
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.USER_AGENT, defaultValue = "") String userAgent,
            HttpServletRequest request
    ) {
        String frontendUrl = "https://skillswap.asia/mentors/" + id.toString();
        
        if (!isSocialBot(userAgent)) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(frontendUrl))
                    .build();
        }

        try {
            MentorDiscoveryDetailResponse mentor = mentorQueryPort.getMentorDetail(id);
            String title = mentor.identity().displayName() + " | Mentor trên SkillSwap";
            String description = mentor.identity().headline() != null ? mentor.identity().headline() : "Khám phá profile mentor trên SkillSwap";
            String imageUrl = mentor.identity().avatarUrl();
            
            String html = generateOgHtml(title, description, imageUrl, frontendUrl);
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
        } catch (Exception e) {
            log.warn("Error serving OG tags for mentor id: {}", id, e);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(frontendUrl))
                    .build();
        }
    }

    private boolean isSocialBot(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return false;
        }
        String uaLower = userAgent.toLowerCase();
        return SOCIAL_BOT_USER_AGENTS.stream().anyMatch(uaLower::contains);
    }

    private String generateOgHtml(String title, String description, String imageUrl, String url) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"vi\">\n<head>\n<meta charset=\"UTF-8\">\n");
        sb.append("<meta property=\"og:title\" content=\"").append(escapeHtml(title)).append("\" />\n");
        sb.append("<meta property=\"og:description\" content=\"").append(escapeHtml(description)).append("\" />\n");
        if (imageUrl != null && !imageUrl.isBlank()) {
            sb.append("<meta property=\"og:image\" content=\"").append(escapeHtml(imageUrl)).append("\" />\n");
        }
        sb.append("<meta property=\"og:url\" content=\"").append(escapeHtml(url)).append("\" />\n");
        sb.append("<meta property=\"og:type\" content=\"website\" />\n");
        sb.append("<meta name=\"twitter:card\" content=\"summary_large_image\" />\n");
        sb.append("<title>").append(escapeHtml(title)).append("</title>\n");
        sb.append("</head>\n<body>\n");
        sb.append("<p>Redirecting to <a href=\"").append(escapeHtml(url)).append("\">").append(escapeHtml(title)).append("</a>...</p>\n");
        sb.append("</body>\n</html>");
        return sb.toString();
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
}
