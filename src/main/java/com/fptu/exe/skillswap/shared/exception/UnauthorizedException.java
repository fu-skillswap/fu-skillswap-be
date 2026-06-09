package com.fptu.exe.skillswap.shared.exception;

//401 Unauthorized
public class UnauthorizedException extends BaseException {
    public UnauthorizedException(ErrorCode errorCode) {
        super(errorCode);
    }
}

