package com.fptu.exe.skillswap.modules.mentor.dto;

public record MentorVerificationRequestActionResult<T>(
        T data,
        boolean created
) {
}
