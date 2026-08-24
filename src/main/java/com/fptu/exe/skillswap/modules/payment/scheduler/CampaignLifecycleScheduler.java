package com.fptu.exe.skillswap.modules.payment.scheduler;

import com.fptu.exe.skillswap.modules.payment.domain.Campaign;
import com.fptu.exe.skillswap.modules.payment.domain.CampaignStatus;
import com.fptu.exe.skillswap.modules.payment.repository.CampaignRepository;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.time.Clock;

@Component
@ConditionalOnProperty(prefix = "application.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class CampaignLifecycleScheduler {

    private final CampaignRepository campaignRepository;
    private TimeProvider timeProvider = TimeProvider.from(Clock.systemUTC());

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setTimeProvider(TimeProvider timeProvider) {
        if (timeProvider != null) {
            this.timeProvider = timeProvider;
        }
    }

    /**
     * Tự động kích hoạt các chiến dịch SCHEDULED khi tới thời điểm startAt.
     * Chạy mỗi 5 phút.
     */
    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void activateScheduledCampaigns() {
        LocalDateTime now = timeProvider.nowBusiness();
        List<Campaign> readyCampaigns = campaignRepository.findScheduledReadyToActivate(CampaignStatus.SCHEDULED, now);

        for (Campaign campaign : readyCampaigns) {
            try {
                campaign.setStatus(CampaignStatus.ACTIVE);
                campaignRepository.save(campaign);
                log.info("CampaignLifecycleScheduler: Auto-activated campaign ID={} name='{}'", campaign.getId(), campaign.getName());
            } catch (RuntimeException ex) {
                log.error("CampaignLifecycleScheduler: Failed to activate campaign ID={}: {}", campaign.getId(), ex.getMessage(), ex);
            }
        }
    }

    /**
     * Tự động kết thúc các chiến dịch ACTIVE khi đã quá thời điểm endAt.
     * Chạy mỗi giờ.
     */
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void endExpiredCampaigns() {
        LocalDateTime now = timeProvider.nowBusiness();
        List<Campaign> expiredCampaigns = campaignRepository.findActiveExpired(CampaignStatus.ACTIVE, now);

        for (Campaign campaign : expiredCampaigns) {
            try {
                campaign.setStatus(CampaignStatus.ENDED);
                campaignRepository.save(campaign);
                log.info("CampaignLifecycleScheduler: Auto-ended campaign ID={} name='{}'", campaign.getId(), campaign.getName());
            } catch (RuntimeException ex) {
                log.error("CampaignLifecycleScheduler: Failed to end campaign ID={}: {}", campaign.getId(), ex.getMessage(), ex);
            }
        }
    }
}
