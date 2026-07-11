package com.fptu.exe.skillswap.modules.payment.repository;

import com.fptu.exe.skillswap.modules.payment.domain.CampaignBenefit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CampaignBenefitRepository extends JpaRepository<CampaignBenefit, UUID> {

    List<CampaignBenefit> findByCampaignIdAndActiveTrue(UUID campaignId);
}
