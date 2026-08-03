package com.fptu.exe.skillswap.shared.exception;

import lombok.Getter;

/** Signals that a direct-slot mutation needs explicit template-version confirmation. */
@Getter
public class GeneratedOccurrenceReplacementException extends BaseException {
    /** Module-owned conflict metadata serialized as ApiResponse.data by the shared handler. */
    private final Object data;

    public GeneratedOccurrenceReplacementException(Object data) {
        super(ErrorCode.GENERATED_OCCURRENCE_REPLACEMENT_REQUIRED);
        this.data = data;
    }
}
