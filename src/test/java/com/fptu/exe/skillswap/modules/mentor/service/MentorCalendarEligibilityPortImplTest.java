package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceDeliveryMode;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorCalendarEligibilityPortImplTest {

    @Mock private MentorProfileRepository mentorProfileRepository;
    @Mock private MentorServiceRepository mentorServiceRepository;
    @InjectMocks private MentorCalendarEligibilityPortImpl port;

    @Test
    void requireVerifiedMentor_shouldRejectPendingProfile() {
        UUID userId = UUID.randomUUID();
        MentorProfile profile = MentorProfile.builder()
                .userId(userId)
                .status(MentorStatus.PENDING_VERIFICATION)
                .build();
        when(mentorProfileRepository.findWithUserByUserId(userId)).thenReturn(Optional.of(profile));

        BaseException exception = assertThrows(BaseException.class, () -> port.requireVerifiedMentor(userId));

        assertEquals(ErrorCode.GOOGLE_CALENDAR_MENTOR_VERIFICATION_REQUIRED, exception.getErrorCode());
    }

    @Test
    void hasActiveOneToOneService_shouldDelegateExactBusinessPredicate() {
        UUID userId = UUID.randomUUID();
        when(mentorServiceRepository.existsByMentorProfileUserIdAndIsActiveTrueAndDeliveryMode(
                userId, MentorServiceDeliveryMode.ONE_TO_ONE
        )).thenReturn(true);

        assertTrue(port.hasActiveOneToOneService(userId));
    }

    @Test
    void requireVerifiedMentor_shouldAcceptActiveVerifiedProfile() {
        UUID userId = UUID.randomUUID();
        MentorProfile profile = MentorProfile.builder()
                .userId(userId)
                .status(MentorStatus.ACTIVE)
                .verifiedAt(LocalDateTime.now())
                .build();
        when(mentorProfileRepository.findWithUserByUserId(userId)).thenReturn(Optional.of(profile));

        port.requireVerifiedMentor(userId);
    }
}
