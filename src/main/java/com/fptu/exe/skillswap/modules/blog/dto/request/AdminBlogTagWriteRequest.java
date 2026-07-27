package com.fptu.exe.skillswap.modules.blog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminBlogTagWriteRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 140) String slug,
        Boolean active
) {
}
