package com.fptu.exe.skillswap.shared.exception;

import lombok.Getter;

import java.util.UUID;

/** A resource version precondition failed without coupling shared error handling to a module. */
@Getter
public class VersionConflictException extends BaseException {

    private final UUID resourceId;
    private final Integer expectedVersion;
    private final Integer currentVersion;

    public VersionConflictException(ErrorCode errorCode,
                                    String message,
                                    UUID resourceId,
                                    Integer expectedVersion,
                                    Integer currentVersion) {
        super(errorCode, message);
        this.resourceId = resourceId;
        this.expectedVersion = expectedVersion;
        this.currentVersion = currentVersion;
    }
}
