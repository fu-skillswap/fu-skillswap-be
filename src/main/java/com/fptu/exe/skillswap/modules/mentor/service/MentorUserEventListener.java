package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.shared.event.UserBannedEvent;
import com.fptu.exe.skillswap.shared.event.UserDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class MentorUserEventListener {

    private final MentorProfileRepository mentorProfileRepository;

    @EventListener
    @Transactional
    public void onUserBanned(UserBannedEvent event) {
        log.info("Handling UserBannedEvent for user: {}", event.getUserId());
        mentorProfileRepository.findById(event.getUserId()).ifPresent(profile -> {
            profile.setAvailable(false);
            mentorProfileRepository.save(profile);
            log.info("Updated MentorProfile isAvailable = false for banned user: {}", event.getUserId());
        });
    }

    @EventListener
    @Transactional
    public void onUserDeleted(UserDeletedEvent event) {
        log.info("Handling UserDeletedEvent for user: {}", event.getUserId());
        mentorProfileRepository.findById(event.getUserId()).ifPresent(profile -> {
            profile.setAvailable(false);
            profile.setStatus(MentorStatus.DRAFT);
            mentorProfileRepository.save(profile);
            log.info("Deactivated MentorProfile for soft-deleted user: {}", event.getUserId());
        });
    }
}
