package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorBookingPolicy;
import com.fptu.exe.skillswap.modules.mentor.dto.request.UpdateMentorBookingPolicyRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorBookingPolicyResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorSchedulingConstraintsResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorBookingPolicyRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.VersionConflictException;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MentorBookingPolicyService {

    private static final int DEFAULT_LEAD_TIME_MINUTES = 120;
    private static final int DEFAULT_HORIZON_DAYS = 30;
    private static final int MAXIMUM_AVAILABILITY_QUERY_DAYS = 31;
    private static final int MAXIMUM_PARENT_SLOT_DURATION_MINUTES = 720;

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

    @Transactional(readOnly = true)
    public MentorBookingPolicyResponse getPolicy(UUID mentorUserId) {
        MentorBookingPolicySnapshot snapshot = getEffectivePolicy(mentorUserId);
        Integer version = mentorBookingPolicyRepository.findByMentorUserId(mentorUserId)
                .map(MentorBookingPolicy::getVersion)
                .orElse(0);
        return new MentorBookingPolicyResponse(
                snapshot.minimumBookingLeadTimeMinutes(),
                snapshot.maximumBookingHorizonDays(),
                snapshot.timezone(),
                version
        );
    }

    @Transactional(readOnly = true)
    public MentorSchedulingConstraintsResponse getSchedulingConstraints() {
        return new MentorSchedulingConstraintsResponse(
                MAXIMUM_AVAILABILITY_QUERY_DAYS,
                MAXIMUM_PARENT_SLOT_DURATION_MINUTES
        );
    }

    @Transactional
    public MentorBookingPolicyResponse updatePolicy(UUID mentorUserId, UpdateMentorBookingPolicyRequest request) {
        if (request.minimumBookingLeadTimeMinutes() == null
                && request.maximumBookingHorizonDays() == null
                && request.timezone() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Phải cập nhật ít nhất một booking policy field");
        }
        userRepository.findById(mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy mentor"));

        MentorBookingPolicy policy = mentorBookingPolicyRepository.findByMentorUserIdForUpdate(mentorUserId)
                .orElseGet(() -> MentorBookingPolicy.builder().mentorUserId(mentorUserId).build());
        int currentVersion = policy.getVersion() == null ? 0 : policy.getVersion();
        if (!request.expectedVersion().equals(currentVersion)) {
            throw new VersionConflictException(
                    ErrorCode.RESOURCE_CONFLICT,
                    "MENTOR_BOOKING_POLICY_VERSION_CONFLICT",
                    mentorUserId,
                    request.expectedVersion(),
                    currentVersion
            );
        }
        if (request.minimumBookingLeadTimeMinutes() != null) {
            policy.setMinimumBookingLeadTimeMinutes(normalizeLeadTime(request.minimumBookingLeadTimeMinutes()));
        }
        if (request.maximumBookingHorizonDays() != null) {
            policy.setMaximumBookingHorizonDays(normalizeHorizon(request.maximumBookingHorizonDays()));
        }
        if (request.timezone() != null) {
            String timezone = request.timezone().trim();
            if (timezone.isEmpty()) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Timezone không được để trống");
            }
            try {
                ZoneId.of(timezone);
            } catch (RuntimeException ex) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Timezone IANA không hợp lệ");
            }
            policy.setTimezone(timezone);
        }
        MentorBookingPolicy saved = mentorBookingPolicyRepository.saveAndFlush(policy);
        return new MentorBookingPolicyResponse(
                saved.getMinimumBookingLeadTimeMinutes(),
                saved.getMaximumBookingHorizonDays(),
                saved.getTimezone(),
                saved.getVersion()
        );
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
