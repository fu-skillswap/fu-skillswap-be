package com.fptu.exe.skillswap.modules.conversation.domain;

/** Role is explicit for group conversations; direct conversations retain the legacy default. */
public enum ConversationParticipantRole {
    MENTOR,
    ATTENDEE,
    DIRECT_PARTICIPANT
}
