package com.fptu.exe.skillswap.modules.booking.domain;

/** Các mốc sau buổi học. Dùng chung để API và scheduler không đưa ra thời hạn khác nhau. */
public final class PostSessionPolicy {

    public static final int MENTEE_REVIEW_WINDOW_HOURS = 24;
    public static final int AUTO_CLOSE_WARNING_HOURS = 23;

    private PostSessionPolicy() {
    }
}
