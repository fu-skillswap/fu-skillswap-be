package com.fptu.exe.skillswap.modules.blog.dto;

import com.fptu.exe.skillswap.modules.blog.domain.BlogAuthorType;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPostStatus;
import com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Full CMS/editor projection. This response is restricted to admin endpoints. */
@Schema(description = "Internal/Admin - không dùng cho FE người dùng. Chi tiết CMS đầy đủ để admin quản lý bài viết, gồm cả trạng thái xuất bản và thông tin media.")
public record AdminBlogPostDetailResponse(
        @Schema(description = "ID bài viết.")
        UUID id,
        String title,
        String slug,
        boolean slugLocked,
        String excerpt,
        String contentMarkdown,
        @Schema(description = "Internal field - FE không cần sử dụng. Mã kiểm tra nội dung phục vụ CMS/cache.")
        String contentHash,
        String coverImageUrl,
        @Schema(description = "Internal field - FE không cần sử dụng. Object key trong storage của ảnh cover.")
        String coverImageObjectKey,
        String ogImageUrl,
        @Schema(description = "Internal field - FE không cần sử dụng. Object key trong storage của ảnh Open Graph.")
        String ogImageObjectKey,
        BlogAuthorType authorType,
        BlogVisibility visibility,
        BlogPostStatus status,
        String seoTitle,
        String seoDescription,
        String canonicalUrl,
        BlogAuthorResponse author,
        BlogAuthorConversionResponse authorConversion,
        List<BlogCategoryResponse> categories,
        List<BlogTagResponse> tags,
        Integer readingTimeMinutes,
        Long viewCount,
        Long likeCount,
        Long bookmarkCount,
        boolean featured,
        Integer featuredOrder,
        LocalDateTime featuredUntil,
        LocalDateTime publishedAt,
        LocalDateTime lastPublishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer version,
        boolean deleted,
        LocalDateTime deletedAt
) {
}
