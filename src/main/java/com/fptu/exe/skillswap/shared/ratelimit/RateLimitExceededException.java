package com.fptu.exe.skillswap.shared.ratelimit;

import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.Getter;

/** Giữ thời điểm fixed-window reset để trả response 429 cho client. */
@Getter
public class RateLimitExceededException extends BaseException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(ErrorCode.TOO_MANY_REQUESTS, message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }
}
