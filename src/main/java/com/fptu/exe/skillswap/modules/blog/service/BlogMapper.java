package com.fptu.exe.skillswap.modules.blog.service;

import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.modules.blog.domain.BlogCategory;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPost;
import com.fptu.exe.skillswap.modules.blog.domain.BlogTag;
import com.fptu.exe.skillswap.modules.blog.dto.AdminBlogPostCardResponse;
import com.fptu.exe.skillswap.modules.blog.dto.AdminBlogPostDetailResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogAuthorConversionResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogAuthorResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogCategoryResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogEngagementState;
import com.fptu.exe.skillswap.modules.blog.dto.BlogPostReaderCardResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogPostReaderDetailResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogTagResponse;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.mentor.service.MentorBlogAuthorSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class BlogMapper {

    private final ObjectProvider<StorageGateway> storageGatewayProvider;

    public BlogPostReaderCardResponse toReaderCard(BlogPost post,
                                                   BlogEngagementState engagement,
                                                   MentorBlogAuthorSummary authorSummary) {
        return new BlogPostReaderCardResponse(
                post.getId(),
                post.getTitle(),
                post.getSlug(),
                post.getExcerpt(),
                resolveImageUrl(post.getCoverImageUrl(), post.getCoverImageObjectKey()),
                toAuthor(post),
                toAuthorConversion(authorSummary),
                toCategoryResponses(post),
                toTagResponses(post),
                post.getReadingTimeMinutes(),
                post.getViewCount(),
                post.getLikeCount(),
                post.getBookmarkCount(),
                engagement != null && engagement.likedByCurrentUser(),
                engagement != null && engagement.bookmarkedByCurrentUser(),
                post.isFeatured(),
                post.getPublishedAt(),
                post.getLastPublishedAt(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }

    public BlogPostReaderDetailResponse toReaderDetail(BlogPost post,
                                                       BlogEngagementState engagement,
                                                       MentorBlogAuthorSummary authorSummary) {
        BlogPostReaderCardResponse card = toReaderCard(post, engagement, authorSummary);
        return new BlogPostReaderDetailResponse(
                card.id(), card.title(), card.slug(), card.excerpt(), card.coverImageUrl(),
                card.author(), card.authorConversion(), card.categories(), card.tags(),
                card.readingTimeMinutes(), card.viewCount(), card.likeCount(), card.bookmarkCount(),
                card.likedByCurrentUser(), card.bookmarkedByCurrentUser(), card.featured(),
                card.publishedAt(), card.lastPublishedAt(), card.createdAt(), card.updatedAt(),
                post.getContentMarkdown(),
                resolveImageUrl(post.getOgImageUrl(), post.getOgImageObjectKey()),
                post.getSeoTitle(),
                post.getSeoDescription(),
                post.getCanonicalUrl()
        );
    }

    public AdminBlogPostCardResponse toAdminCard(BlogPost post) {
        return new AdminBlogPostCardResponse(
                post.getId(), post.getTitle(), post.getSlug(), post.getExcerpt(), post.getCoverImageUrl(),
                toAuthor(post), toCategoryResponses(post), toTagResponses(post),
                post.getReadingTimeMinutes(), post.getViewCount(), post.getLikeCount(), post.getBookmarkCount(),
                post.isFeatured(), post.getPublishedAt(), post.getLastPublishedAt(), post.getCreatedAt(), post.getUpdatedAt(),
                post.getStatus(), post.getVisibility(), post.getAuthorType(), post.getFeaturedOrder(),
                post.getFeaturedUntil(), post.getVersion(), post.getDeletedAt() != null, post.getDeletedAt()
        );
    }

    public AdminBlogPostDetailResponse toAdminDetail(BlogPost post,
                                                     MentorBlogAuthorSummary authorSummary) {
        return new AdminBlogPostDetailResponse(
                post.getId(), post.getTitle(), post.getSlug(), post.isSlugLocked(), post.getExcerpt(),
                post.getContentMarkdown(), post.getContentHash(), post.getCoverImageUrl(), post.getCoverImageObjectKey(),
                post.getOgImageUrl(), post.getOgImageObjectKey(), post.getAuthorType(), post.getVisibility(),
                post.getStatus(), post.getSeoTitle(), post.getSeoDescription(), post.getCanonicalUrl(),
                toAuthor(post), toAuthorConversion(authorSummary), toCategoryResponses(post),
                toTagResponses(post), post.getReadingTimeMinutes(), post.getViewCount(), post.getLikeCount(),
                post.getBookmarkCount(), post.isFeatured(), post.getFeaturedOrder(), post.getFeaturedUntil(),
                post.getPublishedAt(), post.getLastPublishedAt(), post.getCreatedAt(), post.getUpdatedAt(), post.getVersion(),
                post.getDeletedAt() != null, post.getDeletedAt()
        );
    }

    public BlogCategoryResponse toCategory(BlogCategory category) {
        return new BlogCategoryResponse(
                category.getId(), category.getCode(), category.getName(), category.getSlug(),
                category.getDescription(), category.isActive(), category.getDisplayOrder()
        );
    }

    public BlogTagResponse toTag(BlogTag tag) {
        return new BlogTagResponse(tag.getId(), tag.getName(), tag.getSlug(), tag.isActive());
    }

    private String resolveImageUrl(String directUrl, String objectKey) {
        if (hasText(directUrl)) {
            return directUrl;
        }
        if (!hasText(objectKey)) {
            return null;
        }
        StorageGateway storageGateway = storageGatewayProvider.getIfAvailable();
        return storageGateway == null ? null : storageGateway.resolvePublicUrl(objectKey);
    }

    private BlogAuthorResponse toAuthor(BlogPost post) {
        User user = post.getAuthorUser();
        return new BlogAuthorResponse(
                user.getId(), user.getFullName(), user.getAvatarUrl(),
                post.getAuthorType()
        );
    }

    private BlogAuthorConversionResponse toAuthorConversion(MentorBlogAuthorSummary summary) {
        if (summary == null) {
            return null;
        }
        return new BlogAuthorConversionResponse(
                summary.mentorUserId(), summary.headline(), summary.verified(), summary.averageRating(),
                summary.completedSessions(), summary.bookingCtaLabel(), "/mentors/" + summary.mentorUserId()
        );
    }

    private List<BlogCategoryResponse> toCategoryResponses(BlogPost post) {
        return post.getCategories().stream()
                .sorted(Comparator.comparing(BlogCategory::getDisplayOrder).thenComparing(BlogCategory::getName))
                .map(this::toCategory)
                .toList();
    }

    private List<BlogTagResponse> toTagResponses(BlogPost post) {
        return post.getTags().stream()
                .sorted(Comparator.comparing(BlogTag::getName))
                .map(this::toTag)
                .toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
