package com.fptu.exe.skillswap.infrastructure.ai;

import com.fptu.exe.skillswap.infrastructure.ai.dto.AiForumPostEventPayload;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPost;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiForumEventPublisher {

    private final AiProperties aiProperties;
    private final RestTemplateBuilder restTemplateBuilder;

    @Async
    public void publishPostCreatedEvent(ForumPost post) {
        if (!aiProperties.isEnabled()) {
            log.debug("AI integration is disabled. Skipping forum event publish for postId: {}", post.getId());
            return;
        }

        try {
            RestTemplate restTemplate = restTemplateBuilder
                    .setConnectTimeout(Duration.ofMillis(aiProperties.getConnectTimeoutMs()))
                    .setReadTimeout(Duration.ofMillis(aiProperties.getReadTimeoutMs()))
                    .build();

            String url = aiProperties.getBaseUrl().replaceAll("/+$", "") + "/v1/forum/events";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Token", aiProperties.getInternalToken());

            String createdAtIso = post.getCreatedAt() != null
                    ? post.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    : null;

            AiForumPostEventPayload.AuthorProgram program = null;
            if (post.getAuthorProgram() != null) {
                program = new AiForumPostEventPayload.AuthorProgram(
                        post.getAuthorProgram().getCode(),
                        post.getAuthorProgram().getNameVi()
                );
            }

            AiForumPostEventPayload.PostData postData = new AiForumPostEventPayload.PostData(
                    post.getId().toString(),
                    post.getTitle(),
                    post.getContent(),
                    post.getStatus() != null ? post.getStatus().name() : "PUBLISHED",
                    post.getCommentCount() != null ? post.getCommentCount() : 0,
                    post.getAuthorUser() != null && post.getAuthorUser().getId() != null ? post.getAuthorUser().getId().toString() : null,
                    createdAtIso,
                    program
            );

            AiForumPostEventPayload payload = new AiForumPostEventPayload(
                    post.getId().toString(),
                    postData
            );

            HttpEntity<AiForumPostEventPayload> requestEntity = new HttpEntity<>(payload, headers);
            restTemplate.postForEntity(url, requestEntity, String.class);

            log.info("Successfully published forum post created event to AI service for postId: {}", post.getId());
        } catch (Exception e) {
            log.warn("Failed to publish forum post created event to AI service for postId: {}. Error: {}",
                    post.getId(), e.getMessage());
        }
    }
}
