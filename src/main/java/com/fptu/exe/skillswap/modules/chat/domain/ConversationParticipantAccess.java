package com.fptu.exe.skillswap.modules.chat.domain;

/** Access is derived from booking/group lifecycle and persisted only as the current chat gate. */
public enum ConversationParticipantAccess {
    ACTIVE,
    READ_ONLY,
    REVOKED
}
