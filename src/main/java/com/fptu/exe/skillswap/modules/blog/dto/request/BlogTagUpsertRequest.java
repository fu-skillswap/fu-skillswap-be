package com.fptu.exe.skillswap.modules.blog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record BlogTagUpsertRequest(
        UUID id,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 140) String slug,
        Boolean active
) {
}
