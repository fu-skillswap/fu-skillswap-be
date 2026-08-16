package com.fptu.exe.skillswap.shared.exception;

import lombok.Getter;

/** Báo thao tác slot trực tiếp cần xác nhận version của template. */
@Getter
public class GeneratedOccurrenceReplacementException extends BaseException {
/** Dữ liệu conflict của module, được handler chung trả qua ApiResponse.data. */
    private final Object data;

    public GeneratedOccurrenceReplacementException(Object data) {
        super(ErrorCode.GENERATED_OCCURRENCE_REPLACEMENT_REQUIRED);
        this.data = data;
    }
}
