package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.feedback.event.SessionFeedbackSubmittedEvent;
import com.fptu.exe.skillswap.modules.mentor.port.MentorRatingPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MentorRatingEventListener {

    private final MentorRatingPort mentorRatingPort;

    @EventListener
    public void onFeedbackSubmitted(SessionFeedbackSubmittedEvent event) {
        if (event != null && event.mentorUserId() != null) {
            mentorRatingPort.updateRatingStats(event.mentorUserId(), event.rating());
        }
    }
}
