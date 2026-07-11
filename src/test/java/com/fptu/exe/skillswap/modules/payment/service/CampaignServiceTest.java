package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.payment.domain.Campaign;
import com.fptu.exe.skillswap.modules.payment.domain.CampaignBenefit;
import com.fptu.exe.skillswap.modules.payment.domain.CampaignBenefitType;
import com.fptu.exe.skillswap.modules.payment.domain.CampaignStatus;
import com.fptu.exe.skillswap.modules.payment.domain.FundingSource;
import com.fptu.exe.skillswap.modules.payment.repository.CampaignBenefitRepository;
import com.fptu.exe.skillswap.modules.payment.repository.CampaignRepository;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignServiceTest {

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private CampaignBenefitRepository campaignBenefitRepository;

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StudentProfileRepository studentProfileRepository;

    @InjectMocks
    private CampaignService campaignService;

    @Test
    void resolveCampaignCredit_shouldLockCampaignBeforeCalculatingRemainingBudget() {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        User user = new User();
        user.setId(userId);
        user.setRoles(Set.<RoleCode>of());

        Campaign campaign = Campaign.builder()
                .id(campaignId)
                .name("Freshman Support")
                .status(CampaignStatus.ACTIVE)
                .fundingSource(FundingSource.APP_FUNDED)
                .budgetScoin(500)
                .build();

        CampaignBenefit benefit = CampaignBenefit.builder()
                .id(UUID.randomUUID())
                .campaign(campaign)
                .benefitType(CampaignBenefitType.CREDIT_ISSUANCE)
                .creditScoin(300)
                .active(true)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(studentProfileRepository.findWithDetailsByUserId(userId)).thenReturn(Optional.empty());
        when(campaignRepository.findIdsByStatusOrderByIdAsc(CampaignStatus.ACTIVE)).thenReturn(List.of(campaignId));
        when(campaignRepository.findByIdForUpdate(campaignId)).thenReturn(Optional.of(campaign));
        when(campaignBenefitRepository.findByCampaignIdAndActiveTrue(campaignId)).thenReturn(List.of(benefit));
        when(paymentOrderRepository.sumCampaignCreditByCampaignIdAndStatusNotIn(eq(campaignId), anyCollection())).thenReturn(250);

        CampaignService.CampaignCreditApplication result = campaignService.resolveCampaignCredit(
                userId,
                Booking.builder().build(),
                400
        );

        assertEquals(campaignId, result.campaignId());
        assertEquals("Freshman Support", result.campaignName());
        assertEquals(FundingSource.APP_FUNDED, result.fundingSource());
        assertEquals(250, result.appliedScoin());
        verify(campaignRepository).findByIdForUpdate(campaignId);
    }
}
