package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRepeatType;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRuleType;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilityRule;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.dto.response.AvailabilityRuleResponse;
import com.fptu.exe.skillswap.modules.booking.dto.request.UpsertAvailabilityRuleRequest;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilityRuleRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAvailabilitySlotResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MentorAvailabilityService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String APP_TIMEZONE = "Asia/Ho_Chi_Minh";
    private static final int MAX_LOOKAHEAD_DAYS = 31;
    private static final int MAX_GENERATED_SLOTS = 120;
    private static final long MAX_PENDING_REQUESTS_PER_SLOT = 3;
    private static final Set<Integer> ALLOWED_SESSION_DURATIONS = Set.of(15, 30, 60, 90);

    private final MentorProfileRepository mentorProfileRepository;
    private final MentorAvailabilityRuleRepository mentorAvailabilityRuleRepository;
    private final MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;

    @Transactional(readOnly = true)
    public List<AvailabilityRuleResponse> getMyRules(UUID mentorUserId) {
        requireUserId(mentorUserId);
        return mentorAvailabilityRuleRepository
                .findByMentorProfileUserIdAndActiveTrueOrderByEffectiveFromAscStartTimeAsc(mentorUserId)
                .stream()
                .map(this::toRuleResponse)
                .toList();
    }

    @Transactional
    public AvailabilityRuleResponse createRule(UUID mentorUserId, UpsertAvailabilityRuleRequest request) {
        MentorProfile mentorProfile = getManagedActiveMentorProfile(mentorUserId);
        UpsertAvailabilityRuleRequest safeRequest = validateRuleRequest(request);

        MentorAvailabilityRule rule = MentorAvailabilityRule.builder()
                .mentorProfile(mentorProfile)
                .ruleType(safeRequest.ruleType())
                .repeatType(safeRequest.repeatType())
                .daysOfWeek(encodeDays(safeRequest.daysOfWeek()))
                .effectiveFrom(safeRequest.effectiveFrom())
                .effectiveTo(safeRequest.effectiveTo())
                .startTime(normalizedStartTime(safeRequest))
                .endTime(normalizedEndTime(safeRequest))
                .timezone(APP_TIMEZONE)
                .active(true)
                .note(trimToNull(safeRequest.note()))
                .build();

        MentorAvailabilityRule savedRule = mentorAvailabilityRuleRepository.save(rule);
        resetFutureGeneratedSlots(mentorUserId);
        return toRuleResponse(savedRule);
    }

    @Transactional
    public AvailabilityRuleResponse updateRule(UUID mentorUserId, UUID ruleId, UpsertAvailabilityRuleRequest request) {
        requireUserId(mentorUserId);
        if (ruleId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã rule lịch rảnh không hợp lệ");
        }

        MentorAvailabilityRule rule = mentorAvailabilityRuleRepository.findByIdAndMentorProfileUserId(ruleId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy rule lịch rảnh"));
        UpsertAvailabilityRuleRequest safeRequest = validateRuleRequest(request);

        rule.setRuleType(safeRequest.ruleType());
        rule.setRepeatType(safeRequest.repeatType());
        rule.setDaysOfWeek(encodeDays(safeRequest.daysOfWeek()));
        rule.setEffectiveFrom(safeRequest.effectiveFrom());
        rule.setEffectiveTo(safeRequest.effectiveTo());
        rule.setStartTime(normalizedStartTime(safeRequest));
        rule.setEndTime(normalizedEndTime(safeRequest));
        rule.setTimezone(APP_TIMEZONE);
        rule.setNote(trimToNull(safeRequest.note()));

        resetFutureGeneratedSlots(mentorUserId);
        return toRuleResponse(rule);
    }

    @Transactional
    public void deleteRule(UUID mentorUserId, UUID ruleId) {
        requireUserId(mentorUserId);
        if (ruleId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã rule lịch rảnh không hợp lệ");
        }

        MentorAvailabilityRule rule = mentorAvailabilityRuleRepository.findByIdAndMentorProfileUserId(ruleId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy rule lịch rảnh"));
        rule.setActive(false);
        resetFutureGeneratedSlots(mentorUserId);
    }

    @Transactional(readOnly = true)
    public List<MentorAvailabilitySlotResponse> getAvailableSlots(MentorProfile mentorProfile, LocalDate fromDate, LocalDate toDate) {
        if (mentorProfile == null || mentorProfile.getUserId() == null) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy mentor");
        }

        DateRange dateRange = resolveDateRange(fromDate, toDate);

        LocalDateTime fromTime = max(dateRange.fromDate().atStartOfDay(), now());
        LocalDateTime toTimeExclusive = dateRange.toDate().plusDays(1).atStartOfDay();
        return mentorAvailabilitySlotRepository
                .findQueueAvailableSlots(
                        mentorProfile.getUserId(),
                        fromTime,
                        toTimeExclusive,
                        BookingStatus.PENDING,
                        MAX_PENDING_REQUESTS_PER_SLOT
                )
                .stream()
                .limit(MAX_GENERATED_SLOTS)
                .map(slot -> toSlotResponse(slot, mentorProfile.getTeachingMode()))
                .toList();
    }

    @Async("slotGenerationExecutor")
    @Transactional
    public void generateSlotsForMentorAsync(UUID mentorUserId, LocalDate fromDate, LocalDate toDate) {
        try {
            MentorProfile mentorProfile = getManagedActiveMentorProfile(mentorUserId);
            generateSlotsForDateRange(mentorProfile, fromDate, toDate);
        } catch (Exception exception) {
            log.error(
                    "Failed to generate mentor availability slots asynchronously for mentor {} from {} to {}",
                    mentorUserId,
                    fromDate,
                    toDate,
                    exception
            );
        }
    }

    @Transactional
    public void generateSlotsForDateRange(MentorProfile mentorProfile, LocalDate fromDate, LocalDate toDate) {
        List<MentorAvailabilityRule> rules = mentorAvailabilityRuleRepository.findActiveRulesOverlapping(
                mentorProfile.getUserId(),
                fromDate,
                toDate
        );
        if (rules.isEmpty()) {
            return;
        }

        int durationMinutes = requireSessionDuration(mentorProfile.getSessionDuration());
        for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
            List<MentorAvailabilityRule> openRules = matchingRulesForDate(rules, date, AvailabilityRuleType.OPEN);
            if (openRules.isEmpty()) {
                continue;
            }
            List<MentorAvailabilityRule> closedRules = matchingRulesForDate(rules, date, AvailabilityRuleType.CLOSED);
            for (MentorAvailabilityRule openRule : openRules) {
                generateSlotsForOpenRule(mentorProfile, openRule, closedRules, date, durationMinutes);
            }
        }
    }

    private void generateSlotsForOpenRule(
            MentorProfile mentorProfile,
            MentorAvailabilityRule openRule,
            List<MentorAvailabilityRule> closedRules,
            LocalDate date,
            int durationMinutes
    ) {
        LocalDateTime start = LocalDateTime.of(date, openRule.getStartTime());
        LocalDateTime windowEnd = LocalDateTime.of(date, openRule.getEndTime());

        while (!start.plusMinutes(durationMinutes).isAfter(windowEnd)) {
            LocalDateTime end = start.plusMinutes(durationMinutes);
            if (shouldCreateSlot(mentorProfile.getUserId(), start, end, closedRules)) {
                try {
                    mentorAvailabilitySlotRepository.save(MentorAvailabilitySlot.builder()
                            .mentorProfile(mentorProfile)
                            .startTime(start)
                            .endTime(end)
                            .timezone(APP_TIMEZONE)
                            .isActive(true)
                            .isBooked(false)
                            .recurrenceRule(openRule.getRepeatType().name())
                            .build());
                } catch (DataIntegrityViolationException exception) {
                    log.warn(
                            "Skipped overlapping availability slot for mentor {} at {} - {} due to database constraint",
                            mentorProfile.getUserId(),
                            start,
                            end
                    );
                }
            }
            start = end;
        }
    }

    private boolean shouldCreateSlot(
            UUID mentorUserId,
            LocalDateTime start,
            LocalDateTime end,
            List<MentorAvailabilityRule> closedRules
    ) {
        return start.isAfter(now())
                && !overlapsClosedRule(start, end, closedRules)
                && !mentorAvailabilitySlotRepository.existsOverlappingActiveSlot(mentorUserId, start, end);
    }

    private boolean overlapsClosedRule(LocalDateTime slotStart, LocalDateTime slotEnd, List<MentorAvailabilityRule> closedRules) {
        for (MentorAvailabilityRule closedRule : closedRules) {
            if (closedRule.getStartTime() == null || closedRule.getEndTime() == null) {
                return true;
            }
            LocalDateTime closedStart = LocalDateTime.of(slotStart.toLocalDate(), closedRule.getStartTime());
            LocalDateTime closedEnd = LocalDateTime.of(slotStart.toLocalDate(), closedRule.getEndTime());
            if (slotStart.isBefore(closedEnd) && slotEnd.isAfter(closedStart)) {
                return true;
            }
        }
        return false;
    }

    private List<MentorAvailabilityRule> matchingRulesForDate(
            List<MentorAvailabilityRule> rules,
            LocalDate date,
            AvailabilityRuleType ruleType
    ) {
        return rules.stream()
                .filter(rule -> rule.getRuleType() == ruleType)
                .filter(rule -> appliesOnDate(rule, date))
                .sorted(Comparator.comparing(MentorAvailabilityRule::getEffectiveFrom))
                .toList();
    }

    private boolean appliesOnDate(MentorAvailabilityRule rule, LocalDate date) {
        if (date.isBefore(rule.getEffectiveFrom())) {
            return false;
        }
        if (rule.getEffectiveTo() != null && date.isAfter(rule.getEffectiveTo())) {
            return false;
        }
        return switch (rule.getRepeatType()) {
            case NONE -> date.equals(rule.getEffectiveFrom());
            case DAILY -> true;
            case WEEKLY -> decodeDays(rule.getDaysOfWeek()).contains(date.getDayOfWeek());
        };
    }

    private MentorProfile getManagedActiveMentorProfile(UUID mentorUserId) {
        requireUserId(mentorUserId);
        MentorProfile mentorProfile = mentorProfileRepository.findWithUserByUserId(mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ mentor"));
        if (mentorProfile.getUser() == null || mentorProfile.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Tài khoản mentor hiện không thể cấu hình lịch rảnh");
        }
        if (mentorProfile.getStatus() != MentorStatus.ACTIVE || mentorProfile.getVerifiedAt() == null) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ mentor đã được xác thực mới có thể cấu hình lịch rảnh");
        }
        return mentorProfile;
    }

    private UpsertAvailabilityRuleRequest validateRuleRequest(UpsertAvailabilityRuleRequest request) {
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Dữ liệu lịch rảnh không được để trống");
        }
        if (request.ruleType() == null || request.repeatType() == null || request.effectiveFrom() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "ruleType, repeatType và effectiveFrom là bắt buộc");
        }
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "effectiveTo phải lớn hơn hoặc bằng effectiveFrom");
        }
        if (request.repeatType() == AvailabilityRepeatType.WEEKLY
                && (request.daysOfWeek() == null || request.daysOfWeek().isEmpty())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Lịch lặp hằng tuần phải chọn ít nhất một ngày trong tuần");
        }
        if (request.repeatType() != AvailabilityRepeatType.WEEKLY
                && request.daysOfWeek() != null && !request.daysOfWeek().isEmpty()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "daysOfWeek chỉ dùng cho repeatType WEEKLY");
        }
        if (request.ruleType() == AvailabilityRuleType.OPEN) {
            validateTimeRange(request.startTime(), request.endTime());
        }
        if (request.ruleType() == AvailabilityRuleType.CLOSED
                && (request.startTime() != null || request.endTime() != null)) {
            validateTimeRange(request.startTime(), request.endTime());
        }
        return request;
    }

    private void validateTimeRange(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "startTime và endTime là bắt buộc khi mở hoặc đóng một khung giờ cụ thể");
        }
        if (!endTime.isAfter(startTime)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "endTime phải sau startTime");
        }
    }

    private Integer requireSessionDuration(Integer sessionDuration) {
        if (sessionDuration == null || !ALLOWED_SESSION_DURATIONS.contains(sessionDuration)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thời lượng mentoring của mentor chưa hợp lệ");
        }
        return sessionDuration;
    }

    private void resetFutureGeneratedSlots(UUID mentorUserId) {
        mentorAvailabilitySlotRepository.deactivateFutureUnbookedSlots(mentorUserId, now());
    }

    private DateRange resolveDateRange(LocalDate fromDate, LocalDate toDate) {
        LocalDate today = LocalDate.now(APP_ZONE);
        LocalDate safeFrom = fromDate == null || fromDate.isBefore(today) ? today : fromDate;
        LocalDate safeTo = toDate == null ? safeFrom.plusDays(13) : toDate;
        if (safeTo.isBefore(safeFrom)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "toDate phải lớn hơn hoặc bằng fromDate");
        }
        if (ChronoUnit.DAYS.between(safeFrom, safeTo) > MAX_LOOKAHEAD_DAYS) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Chỉ được xem lịch rảnh trong tối đa 31 ngày mỗi lần");
        }
        return new DateRange(safeFrom, safeTo);
    }

    private AvailabilityRuleResponse toRuleResponse(MentorAvailabilityRule rule) {
        return AvailabilityRuleResponse.builder()
                .ruleId(rule.getId())
                .ruleType(rule.getRuleType())
                .repeatType(rule.getRepeatType())
                .daysOfWeek(decodeDays(rule.getDaysOfWeek()).stream().toList())
                .effectiveFrom(rule.getEffectiveFrom())
                .effectiveTo(rule.getEffectiveTo())
                .startTime(rule.getStartTime())
                .endTime(rule.getEndTime())
                .timezone(rule.getTimezone())
                .active(rule.isActive())
                .note(rule.getNote())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }

    private MentorAvailabilitySlotResponse toSlotResponse(MentorAvailabilitySlot slot, TeachingMode teachingMode) {
        return MentorAvailabilitySlotResponse.builder()
                .slotId(slot.getId())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .timezone(slot.getTimezone())
                .durationMinutes((int) ChronoUnit.MINUTES.between(slot.getStartTime(), slot.getEndTime()))
                .teachingMode(teachingMode)
                .recurring(slot.getRecurrenceRule() != null && !slot.getRecurrenceRule().isBlank())
                .build();
    }

    private String encodeDays(List<DayOfWeek> daysOfWeek) {
        if (daysOfWeek == null || daysOfWeek.isEmpty()) {
            return null;
        }
        return daysOfWeek.stream()
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)))
                .stream()
                .sorted()
                .map(DayOfWeek::name)
                .collect(Collectors.joining(","));
    }

    private Set<DayOfWeek> decodeDays(String daysOfWeek) {
        if (daysOfWeek == null || daysOfWeek.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(daysOfWeek.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toCollection(() -> new LinkedHashSet<>()));
    }

    private LocalTime normalizedStartTime(UpsertAvailabilityRuleRequest request) {
        return request.ruleType() == AvailabilityRuleType.CLOSED && request.startTime() == null ? null : request.startTime();
    }

    private LocalTime normalizedEndTime(UpsertAvailabilityRuleRequest request) {
        return request.ruleType() == AvailabilityRuleType.CLOSED && request.endTime() == null ? null : request.endTime();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(APP_ZONE);
    }

    private LocalDateTime max(LocalDateTime left, LocalDateTime right) {
        return left.isAfter(right) ? left : right;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private void requireUserId(UUID userId) {
        if (userId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }

    private record DateRange(LocalDate fromDate, LocalDate toDate) {
    }
}
