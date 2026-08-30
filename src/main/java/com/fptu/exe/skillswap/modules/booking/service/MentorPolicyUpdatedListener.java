package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.mentor.event.MentorBookingPolicyUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MentorPolicyUpdatedListener {

    private final AvailabilityTemplateService availabilityTemplateService;

    @EventListener
    public void onMentorPolicyUpdated(MentorBookingPolicyUpdatedEvent event) {
        if (event != null && event.mentorUserId() != null && availabilityTemplateService != null) {
            availabilityTemplateService.markMentorDue(event.mentorUserId());
        }
    }
}
