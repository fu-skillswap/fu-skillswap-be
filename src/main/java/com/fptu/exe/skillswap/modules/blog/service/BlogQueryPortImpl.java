package com.fptu.exe.skillswap.modules.blog.service;

import com.fptu.exe.skillswap.modules.blog.domain.BlogAuthorType;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPostStatus;
import com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility;
import com.fptu.exe.skillswap.modules.blog.port.BlogMentorArticlePreview;
import com.fptu.exe.skillswap.modules.blog.port.BlogQueryPort;
import com.fptu.exe.skillswap.modules.blog.repository.BlogPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlogQueryPortImpl implements BlogQueryPort {

    private final BlogPostRepository blogPostRepository;
    private final BlogMapper blogMapper;

    @Override
    public long getMentorPublicArticleCount(UUID mentorUserId) {
        if (mentorUserId == null) {
            return 0L;
        }
        BlogPostRepository.MentorPublicAuthorityProjection authority = blogPostRepository.getMentorPublicAuthority(mentorUserId);
        return authority == null ? 0L : authority.getPublishedArticleCount();
    }

    @Override
    public LocalDateTime getMentorLatestPublishedAt(UUID mentorUserId) {
        if (mentorUserId == null) {
            return null;
        }
        BlogPostRepository.MentorPublicAuthorityProjection authority = blogPostRepository.getMentorPublicAuthority(mentorUserId);
        return authority == null ? null : authority.getLatestPublishedAt();
    }

    @Override
    public List<BlogMentorArticlePreview> findMentorPublicProfilePreviews(UUID mentorUserId, int limit) {
        if (mentorUserId == null) {
            return List.of();
        }
        int safeLimit = Math.max(1, limit);
        return blogPostRepository.findMentorPublicProfilePreviews(
                        mentorUserId,
                        BlogAuthorType.MENTOR,
                        BlogPostStatus.PUBLISHED,
                        BlogVisibility.PUBLIC,
                        PageRequest.of(0, safeLimit))
                .stream()
                .map(blogMapper::toMentorPublicArticlePreview)
                .toList();
    }
}
