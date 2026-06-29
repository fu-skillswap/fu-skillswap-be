package com.fptu.exe.skillswap.modules.mentor.event;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class MentorVerificationEmailNotificationEvent {

    public enum EventType {
        APPROVED_EMAIL,
        NEEDS_REVISION_EMAIL
    }

    private final EventType eventType;
    private final UUID requestId;
    private final String recipientEmail;
    private final String recipientName;
    private final String reviewerName;
    private final String reviewNote;
    private final LocalDateTime submittedAt;
    private final LocalDateTime reviewedAt;
}
