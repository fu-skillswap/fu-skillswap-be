package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceDeliveryMode;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.identity.port.MentorCalendarEligibilityPort;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MentorCalendarEligibilityPortImpl implements MentorCalendarEligibilityPort {

    private final MentorProfileRepository mentorProfileRepository;
    private final MentorServiceRepository mentorServiceRepository;

    @Override
    @Transactional(readOnly = true)
    public void requireVerifiedMentor(UUID mentorUserId) {
        MentorProfile profile = mentorUserId == null
                ? null
                : mentorProfileRepository.findWithUserByUserId(mentorUserId).orElse(null);
        if (profile == null
                || profile.getStatus() != MentorStatus.ACTIVE
                || profile.getVerifiedAt() == null) {
            throw new BaseException(
                    ErrorCode.GOOGLE_CALENDAR_MENTOR_VERIFICATION_REQUIRED,
                    "Chỉ mentor đã được duyệt mới được kết nối Google Calendar"
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveOneToOneService(UUID mentorUserId) {
        return mentorUserId != null
                && mentorServiceRepository.existsByMentorProfileUserIdAndIsActiveTrueAndDeliveryMode(
                mentorUserId,
                MentorServiceDeliveryMode.ONE_TO_ONE
        );
    }
}
