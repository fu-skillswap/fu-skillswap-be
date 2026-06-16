package com.fptu.exe.skillswap.modules.mentor.dto.request;

public record MentorVerificationRequestActionResult<T>(
        T data,
        boolean created
) {
}
