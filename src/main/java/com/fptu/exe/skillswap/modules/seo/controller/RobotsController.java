package com.fptu.exe.skillswap.modules.seo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "SEO & Social Sharing", description = "Public endpoints for Open Graph link previews and redirection")
public class RobotsController {

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Get robots.txt", description = "Returns the robots.txt file for search engine crawlers")
    public ResponseEntity<String> getRobotsTxt() {
        String robots = """
                User-agent: *
                Allow: /
                
                Sitemap: https://skillswap.asia/sitemap.xml
                """;
        return ResponseEntity.ok(robots);
    }
}
