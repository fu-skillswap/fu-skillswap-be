package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCampaignCreateRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCampaignStatusRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminCampaignAnalyticsResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminCampaignResponse;
import com.fptu.exe.skillswap.modules.payment.domain.Campaign;
import com.fptu.exe.skillswap.modules.payment.domain.CampaignStatus;
import com.fptu.exe.skillswap.modules.payment.domain.FundingSource;
import com.fptu.exe.skillswap.modules.payment.repository.CampaignBenefitRepository;
import com.fptu.exe.skillswap.modules.payment.repository.CampaignRepository;
import com.fptu.exe.skillswap.modules.payment.repository.CouponRedemptionRepository;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCampaignServiceTest {

    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private CampaignBenefitRepository campaignBenefitRepository;
    @Mock
    private PaymentOrderRepository paymentOrderRepository;
    @Mock
    private CouponRedemptionRepository couponRedemptionRepository;

    @InjectMocks
    private AdminCampaignService adminCampaignService;

    @Nested
    @DisplayName("Create Campaign Tests")
    class CreateCampaignTests {

        @Test
        @DisplayName("Should create draft campaign when startAt is null")
        void createCampaign_draft() {
            UUID adminId = UUID.randomUUID();
            AdminCampaignCreateRequest request = new AdminCampaignCreateRequest(
                    "Back to School",
                    "Description",
                    FundingSource.APP_FUNDED,
                    null,
                    null,
                    100000,
                    Set.of("STUDENT"),
                    Collections.emptySet(),
                    Collections.emptySet(),
                    Collections.emptySet()
            );

            Campaign savedCampaign = Campaign.builder()
                    .id(UUID.randomUUID())
                    .name("Back to School")
                    .description("Description")
                    .status(CampaignStatus.DRAFT)
                    .fundingSource(FundingSource.APP_FUNDED)
                    .budgetScoin(100000)
                    .build();

            when(campaignRepository.save(any(Campaign.class))).thenReturn(savedCampaign);

            AdminCampaignResponse response = adminCampaignService.create(adminId, request);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(CampaignStatus.DRAFT);
            verify(campaignRepository).save(any(Campaign.class));
        }

        @Test
        @DisplayName("Should throw exception when endAt is before startAt")
        void createCampaign_invalidTimeWindow() {
            UUID adminId = UUID.randomUUID();
            LocalDateTime start = DateTimeUtil.now().plusDays(2);
            LocalDateTime end = DateTimeUtil.now().plusDays(1);

            AdminCampaignCreateRequest request = new AdminCampaignCreateRequest(
                    "Invalid Window",
                    "Desc",
                    FundingSource.APP_FUNDED,
                    start,
                    end,
                    50000,
                    null, null, null, null
            );

            assertThatThrownBy(() -> adminCampaignService.create(adminId, request))
                    .isInstanceOf(BaseException.class)
                    .hasMessageContaining("Thời điểm kết thúc phải sau thời điểm bắt đầu");
        }
    }

    @Nested
    @DisplayName("Status Transition Tests")
    class StatusTransitionTests {

        @Test
        @DisplayName("Should activate draft campaign")
        void changeStatus_draftToActive() {
            UUID adminId = UUID.randomUUID();
            UUID campaignId = UUID.randomUUID();
            Campaign campaign = Campaign.builder()
                    .id(campaignId)
                    .name("Test Campaign")
                    .status(CampaignStatus.DRAFT)
                    .budgetScoin(50000)
                    .build();

            when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
            when(campaignRepository.save(any(Campaign.class))).thenReturn(campaign);

            AdminCampaignResponse response = adminCampaignService.changeStatus(adminId, campaignId, new AdminCampaignStatusRequest(CampaignStatus.ACTIVE));

            assertThat(response.status()).isEqualTo(CampaignStatus.ACTIVE);
        }

        @Test
        @DisplayName("Should throw exception for invalid transition (ENDED to ACTIVE)")
        void changeStatus_invalidTransition() {
            UUID adminId = UUID.randomUUID();
            UUID campaignId = UUID.randomUUID();
            Campaign campaign = Campaign.builder()
                    .id(campaignId)
                    .name("Test Campaign")
                    .status(CampaignStatus.ENDED)
                    .build();

            when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

            assertThatThrownBy(() -> adminCampaignService.changeStatus(adminId, campaignId, new AdminCampaignStatusRequest(CampaignStatus.ACTIVE)))
                    .isInstanceOf(BaseException.class)
                    .hasMessageContaining("Không thể chuyển trạng thái campaign");
        }
    }

    @Nested
    @DisplayName("Analytics Tests")
    class AnalyticsTests {

        @Test
        @DisplayName("Should calculate ROI and burn rate correctly")
        void getAnalytics() {
            UUID campaignId = UUID.randomUUID();
            Campaign campaign = Campaign.builder()
                    .id(campaignId)
                    .name("Analytics Campaign")
                    .status(CampaignStatus.ACTIVE)
                    .budgetScoin(100000)
                    .startAt(DateTimeUtil.now().minusDays(5))
                    .build();

            when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
            when(paymentOrderRepository.sumCampaignCreditByCampaignIdAndStatusNotIn(any(), any())).thenReturn(25000);
            when(paymentOrderRepository.countByCampaignIdAndStatusNotIn(any(), any())).thenReturn(10L);
            when(paymentOrderRepository.sumTotalScoinByCampaignIdAndStatusNotIn(any(), any())).thenReturn(150000);

            AdminCampaignAnalyticsResponse analytics = adminCampaignService.getAnalytics(campaignId);

            assertThat(analytics).isNotNull();
            assertThat(analytics.budgetScoin()).isEqualTo(100000);
            assertThat(analytics.budgetUsedScoin()).isEqualTo(25000);
            assertThat(analytics.budgetRemainingScoin()).isEqualTo(75000);
            assertThat(analytics.budgetBurnRate()).isEqualTo(25.0);
            assertThat(analytics.totalBookingsCreated()).isEqualTo(10L);
            assertThat(analytics.totalRevenueScoin()).isEqualTo(150000);
            assertThat(analytics.campaignRoiPercent()).isEqualTo(500.0);
        }
    }
}
