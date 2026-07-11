package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.modules.payment.domain.MentorPayoutProfile;
import com.fptu.exe.skillswap.modules.payment.dto.request.MentorPayoutProfileUpsertRequest;
import com.fptu.exe.skillswap.modules.payment.dto.response.MentorPayoutProfileResponse;
import com.fptu.exe.skillswap.modules.payment.repository.MentorPayoutProfileRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorPayoutProfileServiceTest {

    @Mock
    private MentorPayoutProfileRepository payoutProfileRepository;

    @InjectMocks
    private MentorPayoutProfileService payoutProfileService;

    @Test
    void create_firstProfile_shouldAutoSetDefault() {
        UUID mentorUserId = UUID.randomUUID();
        when(payoutProfileRepository.countByMentorUserIdAndIsDefaultTrue(mentorUserId)).thenReturn(0L);
        when(payoutProfileRepository.save(any(MentorPayoutProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MentorPayoutProfileResponse response = payoutProfileService.create(mentorUserId, new MentorPayoutProfileUpsertRequest(
                "Vo Quang Tam",
                "ACB",
                "Asia Commercial Bank",
                "1234567890",
                false,
                true
        ));

        assertEquals(true, response.isDefault());
        assertEquals("******7890", response.accountNumberMasked());
    }

    @Test
    void getActiveProfileForPayout_inactiveProfile_shouldThrowConflict() {
        UUID mentorUserId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        MentorPayoutProfile profile = MentorPayoutProfile.builder()
                .id(profileId)
                .mentorUserId(mentorUserId)
                .accountHolderName("Vo Quang Tam")
                .bankName("ACB")
                .accountNumber("1234567890")
                .isDefault(true)
                .isActive(false)
                .build();
        when(payoutProfileRepository.findByIdAndMentorUserId(profileId, mentorUserId)).thenReturn(Optional.of(profile));

        assertThrows(BaseException.class, () -> payoutProfileService.getActiveProfileForPayout(mentorUserId, profileId));
    }
}
