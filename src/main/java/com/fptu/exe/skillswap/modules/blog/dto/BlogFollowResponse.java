package com.fptu.exe.skillswap.modules.blog.dto;

import java.util.List;

public record BlogFollowResponse(
        List<BlogCategoryResponse> categories,
        List<BlogTagResponse> tags
) {
}
