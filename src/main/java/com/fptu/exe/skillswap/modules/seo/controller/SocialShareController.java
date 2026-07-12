package com.fptu.exe.skillswap.modules.seo.controller;

import com.fptu.exe.skillswap.modules.blog.dto.BlogPostDetailResponse;
import com.fptu.exe.skillswap.modules.blog.service.BlogService;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorDiscoveryDetailResponse;
import com.fptu.exe.skillswap.modules.mentor.service.MentorDiscoveryService;
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

    private final BlogService blogService;
    private final MentorDiscoveryService mentorDiscoveryService;

    // List of common social media bot user agents
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
            BlogPostDetailResponse post = blogService.getBySlug(null, slug);
            String html = generateOgHtml(
                    post.title() != null ? post.title() : "SkillSwap Blog",
                    post.excerpt() != null ? post.excerpt() : "Đọc bài viết trên SkillSwap",
                    post.ogImageUrl() != null ? post.ogImageUrl() : post.coverImageUrl(),
                    frontendUrl
            );
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
        } catch (Exception e) {
            log.warn("Error serving OG tags for blog slug: {}", slug, e);
            // Fallback to redirect even for bots if not found or error
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
            MentorDiscoveryDetailResponse mentor = mentorDiscoveryService.getMentorDetail(id);
            String title = mentor.displayName() + " | Mentor trên SkillSwap";
            String description = mentor.headline() != null ? mentor.headline() : "Khám phá profile mentor trên SkillSwap";
            String imageUrl = mentor.avatarUrl();
            
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
        if (userAgent == null || userAgent.isBlank()) return false;
        String lowerAgent = userAgent.toLowerCase();
        for (String botPattern : SOCIAL_BOT_USER_AGENTS) {
            if (lowerAgent.contains(botPattern)) {
                return true;
            }
        }
        return false;
    }

    private String generateOgHtml(String title, String description, String imageUrl, String url) {
        title = escapeHtmlAttribute(title);
        description = escapeHtmlAttribute(description);
        imageUrl = escapeHtmlAttribute(imageUrl);
        url = escapeHtmlAttribute(url);
        
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                    <meta charset="UTF-8">
                    <title>%s</title>
                    <meta property="og:title" content="%s" />
                    <meta property="og:description" content="%s" />
                    <meta property="og:type" content="website" />
                    <meta property="og:url" content="%s" />
                    <meta property="og:image" content="%s" />
                    <meta name="twitter:card" content="summary_large_image" />
                    <link rel="canonical" href="%s" />
                </head>
                <body>
                    <p>Đang chuyển hướng... <a href="%s">Bấm vào đây nếu trình duyệt không tự chuyển</a>.</p>
                </body>
                </html>
                """.formatted(title, title, description, url, imageUrl != null ? imageUrl : "", url, url);
    }
    
    private String escapeHtmlAttribute(String value) {
        if (value == null) return "";
        return value.replace("\"", "&quot;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
    }
}
