package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.modules.booking.port.BookingCheckoutSnapshot;
import com.fptu.exe.skillswap.modules.identity.port.StudentProfileRecord;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.identity.port.UserSummaryRecord;
import com.fptu.exe.skillswap.modules.payment.domain.Campaign;
import com.fptu.exe.skillswap.modules.payment.domain.CampaignBenefit;
import com.fptu.exe.skillswap.modules.payment.domain.CampaignBenefitType;
import com.fptu.exe.skillswap.modules.payment.domain.CampaignStatus;
import com.fptu.exe.skillswap.modules.payment.domain.FundingSource;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.payment.repository.CampaignBenefitRepository;
import com.fptu.exe.skillswap.modules.payment.repository.CampaignRepository;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.time.Clock;

@Service
@RequiredArgsConstructor
public class CampaignService {

    private static final List<PaymentOrderStatus> NON_CONSUMING_STATUSES =
            List.of(PaymentOrderStatus.FAILED, PaymentOrderStatus.CANCELLED, PaymentOrderStatus.EXPIRED);

    private final CampaignRepository campaignRepository;
    private final CampaignBenefitRepository campaignBenefitRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final UserQueryPort userQueryPort;
    private TimeProvider timeProvider = TimeProvider.from(Clock.systemUTC());

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setTimeProvider(TimeProvider timeProvider) {
        if (timeProvider != null) {
            this.timeProvider = timeProvider;
        }
    }

    @Transactional
    public CampaignCreditApplication resolveCampaignCredit(UUID userId, BookingCheckoutSnapshot booking, int amountAfterCouponScoin) {
        return resolveCampaignCredit(userId, booking, amountAfterCouponScoin, true);
    }

    /**
     * Calculates a non-binding campaign estimate. Preview callers must never reserve
     * campaign budget or acquire the checkout row lock.
     */
    @Transactional(readOnly = true)
    public CampaignCreditApplication estimateCampaignCredit(UUID userId, BookingCheckoutSnapshot booking, int amountAfterCouponScoin) {
        return resolveCampaignCredit(userId, booking, amountAfterCouponScoin, false);
    }

    private CampaignCreditApplication resolveCampaignCredit(
            UUID userId,
            BookingCheckoutSnapshot booking,
            int amountAfterCouponScoin,
            boolean lockCampaign
    ) {
        if (userId == null || booking == null || amountAfterCouponScoin <= 0) {
            return CampaignCreditApplication.none();
        }

        UserSummaryRecord user = userQueryPort.findUserSummaryById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy người dùng để áp campaign"));
        StudentProfileRecord studentProfile = userQueryPort.findStudentProfileRecordByUserId(userId).orElse(null);

        CampaignCreditApplication bestMatch = CampaignCreditApplication.none();
        for (UUID campaignId : campaignRepository.findIdsByStatusOrderByIdAsc(CampaignStatus.ACTIVE)) {
            Campaign campaign = (lockCampaign
                    ? campaignRepository.findByIdForUpdate(campaignId)
                    : campaignRepository.findById(campaignId)).orElse(null);
            if (campaign == null) {
                continue;
            }
            if (!isWithinWindow(campaign) || !matchesAudience(campaign, user, studentProfile, booking)) {
                continue;
            }

            for (CampaignBenefit benefit : campaignBenefitRepository.findByCampaignIdAndActiveTrue(campaign.getId())) {
                if (benefit.getBenefitType() != CampaignBenefitType.CREDIT_ISSUANCE || benefit.getCreditScoin() == null) {
                    continue;
                }

                int budgetRemaining = calculateBudgetRemaining(campaign);
                if (budgetRemaining <= 0) {
                    continue;
                }

                int applicableAmount = Math.min(Math.min(amountAfterCouponScoin, benefit.getCreditScoin()), budgetRemaining);
                if (applicableAmount <= 0 || applicableAmount <= bestMatch.appliedScoin()) {
                    continue;
                }

                bestMatch = CampaignCreditApplication.builder()
                        .campaignId(campaign.getId())
                        .campaignName(campaign.getName())
                        .fundingSource(campaign.getFundingSource())
                        .appliedScoin(applicableAmount)
                        .build();
            }
        }
        return bestMatch;
    }

    private boolean isWithinWindow(Campaign campaign) {
        if (campaign.getStartAt() != null && timeProvider.nowBusiness().isBefore(campaign.getStartAt())) {
            return false;
        }
        return campaign.getEndAt() == null || !timeProvider.nowBusiness().isAfter(campaign.getEndAt());
    }

    private boolean matchesAudience(Campaign campaign, UserSummaryRecord user, StudentProfileRecord studentProfile, BookingCheckoutSnapshot booking) {
        if (!campaign.getAudienceRoleCodes().isEmpty()
                && (user.roles() == null || user.roles().stream().map(Enum::name).noneMatch(campaign.getAudienceRoleCodes()::contains))) {
            return false;
        }
        if (!campaign.getAudienceCampusIds().isEmpty()
                && (studentProfile == null || studentProfile.campusId() == null
                || !campaign.getAudienceCampusIds().contains(studentProfile.campusId()))) {
            return false;
        }
        if (!campaign.getAudienceProgramIds().isEmpty()
                && (studentProfile == null || studentProfile.programId() == null
                || !campaign.getAudienceProgramIds().contains(studentProfile.programId()))) {
            return false;
        }
        if (!campaign.getAudienceSpecializationIds().isEmpty()
                && (studentProfile == null || studentProfile.specializationId() == null
                || !campaign.getAudienceSpecializationIds().contains(studentProfile.specializationId()))) {
            return false;
        }
        return true;
    }

    private int calculateBudgetRemaining(Campaign campaign) {
        int used = paymentOrderRepository.sumCampaignCreditByCampaignIdAndStatusNotIn(
                campaign.getId(),
                NON_CONSUMING_STATUSES
        );
        return Math.max(0, (campaign.getBudgetScoin() == null ? 0 : campaign.getBudgetScoin()) - used);
    }

    @Builder
    public record CampaignCreditApplication(
            UUID campaignId,
            String campaignName,
            FundingSource fundingSource,
            int appliedScoin
    ) {
        public static CampaignCreditApplication none() {
            return CampaignCreditApplication.builder().appliedScoin(0).build();
        }
    }
}
