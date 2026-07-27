package com.fptu.exe.skillswap.modules.mentor.dto.request;
import com.fptu.exe.skillswap.modules.mentor.domain.*;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record MentorServiceResourceCreateRequest(@NotNull UUID uploadIntentId, @NotBlank @Size(max=255) String title,
    @Size(max=4000) String description, @NotNull MentorServiceResourceType resourceType,
    @NotNull MentorServiceResourceVisibility visibility) {}
