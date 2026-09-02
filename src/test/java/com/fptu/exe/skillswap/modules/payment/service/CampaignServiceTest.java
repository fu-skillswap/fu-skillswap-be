package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.modules.booking.port.BookingCheckoutSnapshot;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.identity.port.UserSummaryRecord;
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
import static org.mockito.Mockito.never;
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
    private UserQueryPort userQueryPort;

    @InjectMocks
    private CampaignService campaignService;

    @Test
    void resolveCampaignCredit_shouldLockCampaignBeforeCalculatingRemainingBudget() {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        UserSummaryRecord user = new UserSummaryRecord(
                userId, "user@test.com", "Test User", null, Set.<RoleCode>of(), "ACTIVE", true);

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

        when(userQueryPort.findUserSummaryById(userId)).thenReturn(Optional.of(user));
        when(userQueryPort.findStudentProfileRecordByUserId(userId)).thenReturn(Optional.empty());
        when(campaignRepository.findIdsByStatusOrderByIdAsc(CampaignStatus.ACTIVE)).thenReturn(List.of(campaignId));
        when(campaignRepository.findByIdForUpdate(campaignId)).thenReturn(Optional.of(campaign));
        when(campaignBenefitRepository.findByCampaignIdAndActiveTrue(campaignId)).thenReturn(List.of(benefit));
        when(paymentOrderRepository.sumCampaignCreditByCampaignIdAndStatusNotIn(eq(campaignId), anyCollection())).thenReturn(250);

        CampaignService.CampaignCreditApplication result = campaignService.resolveCampaignCredit(
                userId,
                checkoutSnapshot(),
                400
        );

        assertEquals(campaignId, result.campaignId());
        assertEquals("Freshman Support", result.campaignName());
        assertEquals(FundingSource.APP_FUNDED, result.fundingSource());
        assertEquals(250, result.appliedScoin());
        verify(campaignRepository).findByIdForUpdate(campaignId);
    }

    @Test
    void estimateCampaignCredit_shouldNotAcquireCheckoutLock() {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        UserSummaryRecord user = new UserSummaryRecord(
                userId, "user@test.com", "Test User", null, Set.<RoleCode>of(), "ACTIVE", true);

        Campaign campaign = Campaign.builder()
                .id(campaignId)
                .name("Preview Campaign")
                .status(CampaignStatus.ACTIVE)
                .fundingSource(FundingSource.APP_FUNDED)
                .budgetScoin(500)
                .build();
        CampaignBenefit benefit = CampaignBenefit.builder()
                .id(UUID.randomUUID())
                .campaign(campaign)
                .benefitType(CampaignBenefitType.CREDIT_ISSUANCE)
                .creditScoin(200)
                .active(true)
                .build();

        when(userQueryPort.findUserSummaryById(userId)).thenReturn(Optional.of(user));
        when(userQueryPort.findStudentProfileRecordByUserId(userId)).thenReturn(Optional.empty());
        when(campaignRepository.findIdsByStatusOrderByIdAsc(CampaignStatus.ACTIVE)).thenReturn(List.of(campaignId));
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
        when(campaignBenefitRepository.findByCampaignIdAndActiveTrue(campaignId)).thenReturn(List.of(benefit));
        when(paymentOrderRepository.sumCampaignCreditByCampaignIdAndStatusNotIn(eq(campaignId), anyCollection())).thenReturn(0);

        CampaignService.CampaignCreditApplication result = campaignService.estimateCampaignCredit(
                userId,
                checkoutSnapshot(),
                300
        );

        assertEquals(200, result.appliedScoin());
        verify(campaignRepository).findById(campaignId);
        verify(campaignRepository, never()).findByIdForUpdate(campaignId);
    }

    private BookingCheckoutSnapshot checkoutSnapshot() {
        return new BookingCheckoutSnapshot(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                false, 10_000, 60, null, "ACCEPTED_AWAITING_PAYMENT", null);
    }
}
