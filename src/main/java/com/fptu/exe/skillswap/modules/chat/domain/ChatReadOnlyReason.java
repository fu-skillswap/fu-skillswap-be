package com.fptu.exe.skillswap.modules.chat.domain;

public enum ChatReadOnlyReason {
    ADMIN_LOCKED,
    ACCOUNT_RESTRICTED,
    UNDER_REVIEW,
    PARTICIPANT_BLOCKED,
    GROUP_SESSION_ENDED,
    GROUP_MEMBERSHIP_REVOKED,
    NO_EFFECTIVE_BOOKING,
    CHAT_WINDOW_EXPIRED
}
