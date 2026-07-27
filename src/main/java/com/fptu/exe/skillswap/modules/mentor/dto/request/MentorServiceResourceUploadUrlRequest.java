package com.fptu.exe.skillswap.modules.mentor.dto.request;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceResourceType;
import jakarta.validation.constraints.*;
public record MentorServiceResourceUploadUrlRequest(@NotBlank @Size(max=255) String filename, @NotNull MentorServiceResourceType resourceType) {}
