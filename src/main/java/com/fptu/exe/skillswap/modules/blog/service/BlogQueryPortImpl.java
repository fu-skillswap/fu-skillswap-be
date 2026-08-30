package com.fptu.exe.skillswap.modules.blog.service;

import com.fptu.exe.skillswap.modules.blog.domain.BlogAuthorType;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPostStatus;
import com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility;
import com.fptu.exe.skillswap.modules.blog.dto.BlogPostReaderDetailResponse;
import com.fptu.exe.skillswap.modules.blog.dto.response.MentorPublicArticlePreviewResponse;
import com.fptu.exe.skillswap.modules.blog.port.BlogQueryPort;
import com.fptu.exe.skillswap.modules.blog.repository.BlogPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BlogQueryPortImpl implements BlogQueryPort {

    private final BlogService blogService;
    private final BlogPostRepository blogPostRepository;

    @Override
    public List<MentorPublicArticlePreviewResponse> findRecentArticlesByMentor(UUID mentorUserId, int limit) {
        return blogService.getMentorPublicArticles(mentorUserId, limit);
    }

    @Override
    public BlogPostReaderDetailResponse getBySlug(String slug) {
        return blogService.getBySlug(null, slug);
    }

    @Override
    public long countPublishedArticlesByMentor(UUID mentorUserId) {
        BlogPostRepository.MentorPublicAuthorityProjection authority = blogPostRepository.getMentorPublicAuthority(mentorUserId);
        return authority == null ? 0L : authority.getPublishedArticleCount();
    }

    @Override
    public LocalDateTime getLatestPublishedArticleDate(UUID mentorUserId) {
        BlogPostRepository.MentorPublicAuthorityProjection authority = blogPostRepository.getMentorPublicAuthority(mentorUserId);
        return authority == null ? null : authority.getLatestPublishedAt();
    }
}
