package com.fptu.exe.skillswap.modules.mentor.dto.request;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceResourceVisibility;
import jakarta.validation.constraints.*;
public record MentorServiceResourceUpdateRequest(@NotBlank @Size(max=255) String title, @Size(max=4000) String description,
    @NotNull MentorServiceResourceVisibility visibility, @NotNull @PositiveOrZero Integer expectedVersion) {}
