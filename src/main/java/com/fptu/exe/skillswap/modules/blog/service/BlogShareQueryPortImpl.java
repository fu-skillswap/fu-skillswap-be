package com.fptu.exe.skillswap.modules.blog.service;

import com.fptu.exe.skillswap.modules.blog.dto.BlogPostReaderDetailResponse;
import com.fptu.exe.skillswap.modules.blog.port.BlogShareMetadata;
import com.fptu.exe.skillswap.modules.blog.port.BlogShareQueryPort;
import com.fptu.exe.skillswap.modules.blog.repository.BlogPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Adapts blog's internal reader use case to its small SEO-facing public contract. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlogShareQueryPortImpl implements BlogShareQueryPort {

    private final BlogService blogService;
    private final BlogPostRepository blogPostRepository;

    @Override
    public BlogShareMetadata findPublishedShareMetadata(String slug) {
        BlogPostReaderDetailResponse post = blogService.getBySlug(null, slug);
        String imageUrl = post.ogImageUrl() != null ? post.ogImageUrl() : post.coverImageUrl();
        return new BlogShareMetadata(post.title(), post.excerpt(), imageUrl);
    }

    @Override
    public List<String> findPublicPublishedSlugs() {
        return blogPostRepository.findPublicPublishedSlugs();
    }
}
