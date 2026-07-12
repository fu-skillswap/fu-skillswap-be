package com.fptu.exe.skillswap.infrastructure.security;

import com.fptu.exe.skillswap.ProjectApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ProjectApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicApiGrowthGateSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousShouldAccessPublicCatalogWithCacheHeaders() throws Exception {
        mockMvc.perform(get("/api/campuses"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=86400"))
                .andExpect(header().string("ETag", "\"academic-catalog-v1\""));

        mockMvc.perform(get("/api/catalog/help-topics"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=86400"))
                .andExpect(header().string("ETag", "\"catalog-v1\""));
    }

    @Test
    void anonymousShouldAccessPublicBlogReadButNotBlogActions() throws Exception {
        mockMvc.perform(get("/api/blog/posts"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/blog/categories"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/blog/posts/018f3abf-0a22-7112-9748-6cf000c47b6e/like"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/blog/tags/018f3abf-0a22-7112-9748-6cf000c47b6e/follow"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/me/blog/feed"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousShouldAccessMentorPublicReadButNotPrivateDiscoveryActions() throws Exception {
        mockMvc.perform(get("/api/mentors"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/mentors/recommendations"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/mentors/018f3abf-0a22-7112-9748-6cf000c47b6e/availability-slots"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousBlogViewShouldBePublicLimitedWrite() throws Exception {
        mockMvc.perform(post("/api/blog/posts/018f3abf-0a22-7112-9748-6cf000c47b6e/view")
                        .header("User-Agent", "test-agent")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isNotFound());
    }
}
