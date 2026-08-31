package com.fptu.exe.skillswap.modules.booking.repository;

import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilityRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Deprecated(forRemoval = false)
public interface MentorAvailabilityRuleRepository extends JpaRepository<MentorAvailabilityRule, UUID> {

    List<MentorAvailabilityRule> findByMentorUserIdAndActiveTrueOrderByEffectiveFromAscStartTimeAsc(UUID mentorUserId);

    Optional<MentorAvailabilityRule> findByIdAndMentorUserId(UUID ruleId, UUID mentorUserId);

    @Query("""
            select rule
            from MentorAvailabilityRule rule
            where rule.mentorUserId = :mentorUserId
              and rule.active = true
              and rule.effectiveFrom <= :rangeEnd
              and (rule.effectiveTo is null or rule.effectiveTo >= :rangeStart)
            order by rule.effectiveFrom asc, rule.startTime asc
            """)
    List<MentorAvailabilityRule> findActiveRulesOverlapping(
            @Param("mentorUserId") UUID mentorUserId,
            @Param("rangeStart") LocalDate rangeStart,
            @Param("rangeEnd") LocalDate rangeEnd
    );
}
