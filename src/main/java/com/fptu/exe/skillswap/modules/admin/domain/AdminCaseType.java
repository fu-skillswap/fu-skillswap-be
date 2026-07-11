package com.fptu.exe.skillswap.modules.admin.domain;

import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;

import java.util.Locale;

public enum AdminCaseType {
    MENTOR_VERIFICATION_REQUEST,
    BOOKING,
    FORUM_REPORT,
    PAYOUT_REQUEST,
    PAYMENT_ORDER,
    EMAIL_OUTBOX;

    public static AdminCaseType parse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "caseType không hợp lệ");
        }
        try {
            return valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "caseType không hợp lệ");
        }
    }
}
