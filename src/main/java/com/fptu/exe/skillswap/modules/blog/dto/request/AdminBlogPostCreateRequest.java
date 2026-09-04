package com.fptu.exe.skillswap.modules.blog.dto.request;

import com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "Internal/Admin - không dùng cho FE người dùng. Tạo bài viết trong CMS; backend kiểm tra quyền admin và asset được phép sử dụng.")
public record AdminBlogPostCreateRequest(
        @NotBlank @Size(max = 220) String title,
        @Size(max = 240) String slug,
        String excerpt,
        String contentMarkdown,
        @Schema(description = "URL ảnh cover hiển thị; backend cần kiểm tra URL thuộc asset hợp lệ.", nullable = true) String coverImageUrl,
        @Schema(description = "Internal field - FE không cần sử dụng. Object key ảnh cover trong storage.", nullable = true) String coverImageObjectKey,
        @Schema(description = "URL ảnh Open Graph; backend cần kiểm tra URL thuộc asset hợp lệ.", nullable = true) String ogImageUrl,
        @Schema(description = "Internal field - FE không cần sử dụng. Object key ảnh Open Graph trong storage.", nullable = true) String ogImageObjectKey,
        BlogVisibility visibility,
        @Size(max = 220) String seoTitle,
        @Size(max = 320) String seoDescription,
        String canonicalUrl,
        List<UUID> categoryIds,
        List<UUID> tagIds
) implements AdminBlogPostWriteRequest {
}
