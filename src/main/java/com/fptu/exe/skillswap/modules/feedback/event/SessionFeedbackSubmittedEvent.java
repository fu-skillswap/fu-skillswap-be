package com.fptu.exe.skillswap.modules.feedback.event;

import java.util.UUID;

public record SessionFeedbackSubmittedEvent(
        UUID mentorUserId,
        int rating
) {}
