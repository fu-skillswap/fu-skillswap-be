package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
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
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CampaignService {

    private static final List<PaymentOrderStatus> NON_CONSUMING_STATUSES =
            List.of(PaymentOrderStatus.FAILED, PaymentOrderStatus.CANCELLED, PaymentOrderStatus.EXPIRED);

    private final CampaignRepository campaignRepository;
    private final CampaignBenefitRepository campaignBenefitRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;

    @Transactional(readOnly = true)
    public CampaignCreditApplication resolveCampaignCredit(UUID userId, Booking booking, int amountAfterCouponScoin) {
        if (userId == null || booking == null || amountAfterCouponScoin <= 0) {
            return CampaignCreditApplication.none();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy người dùng để áp campaign"));
        StudentProfile studentProfile = studentProfileRepository.findWithDetailsByUserId(userId).orElse(null);

        CampaignCreditApplication bestMatch = CampaignCreditApplication.none();
        for (Campaign campaign : campaignRepository.findByStatus(CampaignStatus.ACTIVE)) {
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
        if (campaign.getStartAt() != null && DateTimeUtil.now().isBefore(campaign.getStartAt())) {
            return false;
        }
        return campaign.getEndAt() == null || !DateTimeUtil.now().isAfter(campaign.getEndAt());
    }

    private boolean matchesAudience(Campaign campaign, User user, StudentProfile studentProfile, Booking booking) {
        if (!campaign.getAudienceRoleCodes().isEmpty()
                && user.getRoles().stream().map(Enum::name).noneMatch(campaign.getAudienceRoleCodes()::contains)) {
            return false;
        }
        if (!campaign.getAudienceCampusIds().isEmpty()
                && (studentProfile == null || studentProfile.getCampus() == null
                || !campaign.getAudienceCampusIds().contains(studentProfile.getCampus().getId()))) {
            return false;
        }
        if (!campaign.getAudienceProgramIds().isEmpty()
                && (studentProfile == null || studentProfile.getProgram() == null
                || !campaign.getAudienceProgramIds().contains(studentProfile.getProgram().getId()))) {
            return false;
        }
        if (!campaign.getAudienceSpecializationIds().isEmpty()
                && (studentProfile == null || studentProfile.getSpecialization() == null
                || !campaign.getAudienceSpecializationIds().contains(studentProfile.getSpecialization().getId()))) {
            return false;
        }
        if (!campaign.getAudienceHelpTopicIds().isEmpty()) {
            Set<UUID> bookingHelpTopicIds = booking.getService() == null
                    ? Set.of()
                    : booking.getService().getHelpTopics().stream().map(Tag::getId).collect(java.util.stream.Collectors.toSet());
            if (bookingHelpTopicIds.stream().noneMatch(campaign.getAudienceHelpTopicIds()::contains)) {
                return false;
            }
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
