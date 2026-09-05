package com.fptu.exe.skillswap.infrastructure.websocket;

import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.Getter;

/** A typed, client-safe failure raised while processing an inbound STOMP frame. */
@Getter
public class StompErrorException extends RuntimeException {

    private final ErrorCode errorCode;

    public StompErrorException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
