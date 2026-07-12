package com.fptu.exe.skillswap.modules.blog.service;

import com.fptu.exe.skillswap.modules.blog.domain.BlogCategory;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPost;
import com.fptu.exe.skillswap.modules.blog.domain.BlogTag;
import com.fptu.exe.skillswap.modules.blog.dto.BlogAuthorResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogAuthorConversionResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogCategoryResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogEngagementState;
import com.fptu.exe.skillswap.modules.blog.dto.BlogPostCardResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogPostDetailResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogTagResponse;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.mentor.service.MentorBlogAuthorSummary;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Component
public class BlogMapper {

    public BlogPostCardResponse toCard(BlogPost post) {
        return toCard(post, BlogEngagementState.empty(), null);
    }

    public BlogPostCardResponse toCard(BlogPost post,
                                       BlogEngagementState engagement,
                                       MentorBlogAuthorSummary authorSummary) {
        return new BlogPostCardResponse(
                post.getId(),
                post.getTitle(),
                post.getSlug(),
                post.getExcerpt(),
                post.getCoverImageUrl(),
                post.getOgImageUrl(),
                post.getAudienceType(),
                post.getVisibility(),
                post.getStatus(),
                toAuthor(post.getAuthorUser()),
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
                post.getFeaturedOrder(),
                post.getFeaturedUntil(),
                post.getPublishedAt(),
                post.getLastPublishedAt(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }

    public BlogPostDetailResponse toDetail(BlogPost post) {
        return toDetail(post, BlogEngagementState.empty(), null);
    }

    public BlogPostDetailResponse toDetail(BlogPost post,
                                           BlogEngagementState engagement,
                                           MentorBlogAuthorSummary authorSummary) {
        return new BlogPostDetailResponse(
                post.getId(),
                post.getTitle(),
                post.getSlug(),
                post.isSlugLocked(),
                post.getExcerpt(),
                post.getContentMarkdown(),
                post.getContentHash(),
                post.getCoverImageUrl(),
                post.getCoverImageObjectKey(),
                post.getOgImageUrl(),
                post.getOgImageObjectKey(),
                post.getAudienceType(),
                post.getVisibility(),
                post.getStatus(),
                post.getSeoTitle(),
                post.getSeoDescription(),
                post.getCanonicalUrl(),
                toAuthor(post.getAuthorUser()),
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
                post.getFeaturedOrder(),
                post.getFeaturedUntil(),
                post.getPublishedAt(),
                post.getLastPublishedAt(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.getVersion()
        );
    }

    public BlogCategoryResponse toCategory(BlogCategory category) {
        return new BlogCategoryResponse(
                category.getId(),
                category.getCode(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.isActive(),
                category.getDisplayOrder()
        );
    }

    public BlogTagResponse toTag(BlogTag tag) {
        return new BlogTagResponse(tag.getId(), tag.getName(), tag.getSlug(), tag.isActive());
    }

    private BlogAuthorResponse toAuthor(User user) {
        Set<RoleCode> roles = user.getRoles();
        return new BlogAuthorResponse(
                user.getId(),
                user.getFullName(),
                user.getAvatarUrl(),
                roles,
                roles != null && roles.contains(RoleCode.MENTOR)
        );
    }

    private BlogAuthorConversionResponse toAuthorConversion(MentorBlogAuthorSummary summary) {
        if (summary == null) {
            return null;
        }
        return new BlogAuthorConversionResponse(
                summary.mentorUserId(),
                summary.headline(),
                summary.verified(),
                summary.averageRating(),
                summary.completedSessions(),
                summary.bookingCtaLabel(),
                "/mentors/" + summary.mentorUserId()
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
}
