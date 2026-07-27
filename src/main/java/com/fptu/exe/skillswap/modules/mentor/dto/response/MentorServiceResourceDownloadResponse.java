package com.fptu.exe.skillswap.modules.mentor.dto.response;
import java.time.Instant;
public record MentorServiceResourceDownloadResponse(String downloadUrl, Instant expiresAt, String presentationMode) {}
