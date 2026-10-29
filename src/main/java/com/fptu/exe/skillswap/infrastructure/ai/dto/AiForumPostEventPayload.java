package com.fptu.exe.skillswap.infrastructure.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiForumPostEventPayload(
        @JsonProperty("postId") String postId,
        @JsonProperty("post") PostData post
) {
    public record PostData(
            @JsonProperty("postId") String postId,
            @JsonProperty("title") String title,
            @JsonProperty("content") String content,
            @JsonProperty("status") String status,
            @JsonProperty("commentCount") int commentCount,
            @JsonProperty("authorUserId") String authorUserId,
            @JsonProperty("createdAt") String createdAt,
            @JsonProperty("authorProgram") AuthorProgram authorProgram
    ) {}

    public record AuthorProgram(
            @JsonProperty("code") String code,
            @JsonProperty("name") String name
    ) {}
}
