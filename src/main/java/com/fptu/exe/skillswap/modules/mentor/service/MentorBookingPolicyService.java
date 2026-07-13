package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorBookingPolicy;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorBookingPolicyRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MentorBookingPolicyService {

    private static final int DEFAULT_LEAD_TIME_MINUTES = 120;
    private static final int DEFAULT_HORIZON_DAYS = 30;

    private final MentorBookingPolicyRepository mentorBookingPolicyRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public MentorBookingPolicySnapshot getEffectivePolicy(UUID mentorUserId) {
        if (mentorUserId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "mentorUserId không được để trống");
        }
        return mentorBookingPolicyRepository.findByMentorUserId(mentorUserId)
                .map(MentorBookingPolicySnapshot::from)
                .orElseGet(MentorBookingPolicySnapshot::defaults);
    }

    @Transactional
    public MentorBookingPolicySnapshot upsertPolicy(UUID mentorUserId,
                                                    Integer minimumBookingLeadTimeMinutes,
                                                    Integer maximumBookingHorizonDays,
                                                    String timezone) {
        if (mentorUserId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "mentorUserId không được để trống");
        }
        userRepository.findById(mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy mentor"));

        boolean hasPayload = minimumBookingLeadTimeMinutes != null
                || maximumBookingHorizonDays != null
                || timezone != null;
        MentorBookingPolicy existing = mentorBookingPolicyRepository.findByMentorUserIdForUpdate(mentorUserId).orElse(null);
        if (existing == null && !hasPayload) {
            return MentorBookingPolicySnapshot.defaults();
        }

        MentorBookingPolicy policy = existing == null
                ? MentorBookingPolicy.builder().mentorUserId(mentorUserId).build()
                : existing;
        if (minimumBookingLeadTimeMinutes != null) {
            policy.setMinimumBookingLeadTimeMinutes(normalizeLeadTime(minimumBookingLeadTimeMinutes));
        } else if (policy.getMinimumBookingLeadTimeMinutes() == null) {
            policy.setMinimumBookingLeadTimeMinutes(DEFAULT_LEAD_TIME_MINUTES);
        }
        if (maximumBookingHorizonDays != null) {
            policy.setMaximumBookingHorizonDays(normalizeHorizon(maximumBookingHorizonDays));
        } else if (policy.getMaximumBookingHorizonDays() == null) {
            policy.setMaximumBookingHorizonDays(DEFAULT_HORIZON_DAYS);
        }
        if (timezone != null && !timezone.isBlank()) {
            policy.setTimezone(timezone.trim());
        } else if (policy.getTimezone() == null || policy.getTimezone().isBlank()) {
            policy.setTimezone(DateTimeUtil.ZONE_HCM);
        }
        return MentorBookingPolicySnapshot.from(mentorBookingPolicyRepository.save(policy));
    }

    @Transactional(readOnly = true)
    public void validateBookingWindow(UUID mentorUserId, LocalDateTime selectedStartTime, LocalDateTime now) {
        if (mentorUserId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "mentorUserId không được để trống");
        }
        if (selectedStartTime == null || now == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "selectedStartTime và thời gian hiện tại là bắt buộc");
        }
        MentorBookingPolicySnapshot policy = getEffectivePolicy(mentorUserId);
        LocalDateTime earliestAllowed = now.plusMinutes(policy.minimumBookingLeadTimeMinutes());
        if (!selectedStartTime.isAfter(earliestAllowed.minusNanos(1))) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT,
                    "Chỉ được đặt lịch trước ít nhất " + policy.minimumBookingLeadTimeMinutes() + " phút");
        }
        LocalDateTime latestAllowed = now.plusDays(policy.maximumBookingHorizonDays()).plusNanos(1);
        if (selectedStartTime.isAfter(latestAllowed)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT,
                    "Chỉ được đặt lịch trong vòng " + policy.maximumBookingHorizonDays() + " ngày tới");
        }
    }

    @Transactional(readOnly = true)
    public boolean isBookableStartTime(UUID mentorUserId, LocalDateTime selectedStartTime, LocalDateTime now) {
        if (mentorUserId == null || selectedStartTime == null || now == null) {
            return false;
        }
        MentorBookingPolicySnapshot policy = getEffectivePolicy(mentorUserId);
        LocalDateTime earliestAllowed = now.plusMinutes(policy.minimumBookingLeadTimeMinutes());
        LocalDateTime latestAllowed = now.plusDays(policy.maximumBookingHorizonDays());
        return selectedStartTime.isAfter(earliestAllowed.minusNanos(1))
                && !selectedStartTime.isAfter(latestAllowed);
    }

    private int normalizeLeadTime(Integer value) {
        return Math.max(0, value);
    }

    private int normalizeHorizon(Integer value) {
        return Math.max(1, value);
    }

    public record MentorBookingPolicySnapshot(
            Integer minimumBookingLeadTimeMinutes,
            Integer maximumBookingHorizonDays,
            String timezone
    ) {
        public static MentorBookingPolicySnapshot defaults() {
            return new MentorBookingPolicySnapshot(DEFAULT_LEAD_TIME_MINUTES, DEFAULT_HORIZON_DAYS, DateTimeUtil.ZONE_HCM);
        }

        public static MentorBookingPolicySnapshot from(MentorBookingPolicy policy) {
            if (policy == null) {
                return defaults();
            }
            return new MentorBookingPolicySnapshot(
                    policy.getMinimumBookingLeadTimeMinutes() == null ? DEFAULT_LEAD_TIME_MINUTES : policy.getMinimumBookingLeadTimeMinutes(),
                    policy.getMaximumBookingHorizonDays() == null ? DEFAULT_HORIZON_DAYS : policy.getMaximumBookingHorizonDays(),
                    policy.getTimezone() == null || policy.getTimezone().isBlank() ? DateTimeUtil.ZONE_HCM : policy.getTimezone()
            );
        }
    }
}
