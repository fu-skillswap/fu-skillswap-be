package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.constant.BookingQueueConstants;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRepeatType;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRuleType;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilitySlotService;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilitySlotServiceId;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilityRule;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.dto.request.ReplaceAvailabilitySlotServicesRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.UpsertAvailabilityRuleRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateAvailabilitySlotRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.UpdateAvailabilitySlotRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.AvailabilityRuleResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.AvailabilitySlotServiceBasicResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.MentorManagedAvailabilitySlotResponse;
import com.fptu.exe.skillswap.modules.booking.repository.AvailabilitySlotServiceRepository;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilityRuleRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.booking.repository.projection.BookingSegmentPendingCountProjection;
import com.fptu.exe.skillswap.modules.booking.support.AvailabilityCalendarWindowCalculator;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAvailabilitySlotResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.ServiceSlotCandidateItemResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.ServiceSlotCandidatesResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MentorAvailabilityService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String APP_TIMEZONE = "Asia/Ho_Chi_Minh";
    private static final String RULE_UPDATED_PENDING_REJECTION_REASON = "Khung giờ mentoring đã được mentor cập nhật nên yêu cầu chờ không còn hiệu lực.";
    private static final String RULE_DELETED_PENDING_REJECTION_REASON = "Khung giờ mentoring đã bị mentor gỡ khỏi lịch nên yêu cầu chờ không còn hiệu lực.";
    private static final String BLOCKED_BY_ACCEPTED_REASON = "Đã có booking được mentor chấp nhận trùng với khoảng thời gian này";
    private static final String BLOCKED_BY_PENDING_QUOTA_REASON = "Segment này đã đạt tối đa 3 yêu cầu chờ xác nhận";
    private static final String BLOCKED_BY_PAST_TIME_REASON = "Segment này đã bắt đầu hoặc đã trôi qua";

    private final MentorProfileRepository mentorProfileRepository;
    private final MentorAvailabilityRuleRepository mentorAvailabilityRuleRepository;
    private final MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;
    private final AvailabilitySlotServiceRepository availabilitySlotServiceRepository;
    private final MentorServiceRepository mentorServiceRepository;
    private final BookingRepository bookingRepository;
    private final NotificationService notificationService;
    private final AvailabilityCalendarWindowCalculator calendarWindowCalculator;

    @Deprecated(forRemoval = false)
    @Transactional(readOnly = true)
    public List<AvailabilityRuleResponse> getMyRules(UUID mentorUserId) {
        requireUserId(mentorUserId);
        return mentorAvailabilityRuleRepository
                .findByMentorProfileUserIdAndActiveTrueOrderByEffectiveFromAscStartTimeAsc(mentorUserId)
                .stream()
                .map(this::toRuleResponse)
                .toList();
    }

    @Deprecated(forRemoval = false)
    @Transactional
    public AvailabilityRuleResponse createRule(UUID mentorUserId, UpsertAvailabilityRuleRequest request) {
        throw new BaseException(ErrorCode.BAD_REQUEST, "API này đã lỗi thời và không còn được hỗ trợ. Hãy sử dụng API slots mới.");
    }

    @Deprecated(forRemoval = false)
    @Transactional
    public AvailabilityRuleResponse updateRule(UUID mentorUserId, UUID ruleId, UpsertAvailabilityRuleRequest request) {
        throw new BaseException(ErrorCode.BAD_REQUEST, "API này đã lỗi thời và không còn được hỗ trợ. Hãy sử dụng API slots mới.");
    }

    @Deprecated(forRemoval = false)
    @Transactional
    public void deleteRule(UUID mentorUserId, UUID ruleId) {
        throw new BaseException(ErrorCode.BAD_REQUEST, "API này đã lỗi thời và không còn được hỗ trợ. Hãy sử dụng API slots mới.");
    }

    @Transactional
    public MentorManagedAvailabilitySlotResponse createSlotDirectly(UUID mentorUserId, CreateAvailabilitySlotRequest request) {
        requireUserId(mentorUserId);
        MentorProfile mentorProfile = getManagedActiveMentorProfile(mentorUserId);

        LocalDateTime start = request.startTime();
        LocalDateTime end = request.endTime();

        if (start == null || end == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thời gian bắt đầu và kết thúc là bắt buộc");
        }
        if (!end.isAfter(start)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thời gian kết thúc phải sau thời gian bắt đầu");
        }
        if (!start.isAfter(now())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Không thể tạo slot rảnh ở quá khứ");
        }

        if (mentorAvailabilitySlotRepository.existsOverlappingActiveSlot(mentorUserId, start, end)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Khung giờ này đã bị trùng lặp với lịch rảnh khác của bạn");
        }

        MentorAvailabilityRule rule = MentorAvailabilityRule.builder()
                .mentorProfile(mentorProfile)
                .ruleType(AvailabilityRuleType.OPEN)
                .repeatType(AvailabilityRepeatType.NONE)
                .daysOfWeek(null)
                .effectiveFrom(start.toLocalDate())
                .effectiveTo(start.toLocalDate())
                .startTime(start.toLocalTime())
                .endTime(end.toLocalTime())
                .timezone(APP_TIMEZONE)
                .active(true)
                .note(trimToNull(request.note()))
                .build();
        MentorAvailabilityRule savedRule = mentorAvailabilityRuleRepository.save(rule);

        MentorAvailabilitySlot slot = MentorAvailabilitySlot.builder()
                .mentorProfile(mentorProfile)
                .rule(savedRule)
                .startTime(start)
                .endTime(end)
                .timezone(APP_TIMEZONE)
                .isActive(true)
                .isBooked(false)
                .recurrenceRule(AvailabilityRepeatType.NONE.name())
                .note(trimToNull(request.note()))
                .build();
        MentorAvailabilitySlot savedSlot = mentorAvailabilitySlotRepository.save(slot);

        List<MentorService> services;
        if (request.serviceIds() == null || request.serviceIds().isEmpty()) {
            services = mentorServiceRepository.findByMentorProfileUserIdAndIsActiveTrueOrderByCreatedAtAsc(mentorUserId);
        } else {
            services = mentorServiceRepository.findAllById(request.serviceIds());
            for (MentorService service : services) {
                if (service.getMentorProfile() == null || !service.getMentorProfile().getUserId().equals(mentorUserId) || !service.isActive()) {
                    throw new BaseException(ErrorCode.ACCESS_DENIED, "Dịch vụ không hợp lệ hoặc không thuộc về bạn");
                }
            }
        }

        if (!services.isEmpty()) {
            List<AvailabilitySlotService> slotServices = services.stream()
                    .map(service -> buildSlotServiceBinding(savedSlot, service))
                    .toList();
            replaceSlotServices(savedSlot, slotServices);
        }

        return toManagedSlotResponse(savedSlot);
    }

    @Transactional
    public MentorManagedAvailabilitySlotResponse updateSlotDirectly(UUID mentorUserId, UUID slotId, UpdateAvailabilitySlotRequest request) {
        requireUserId(mentorUserId);
        if (slotId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã slot không hợp lệ");
        }

        MentorAvailabilitySlot slot = mentorAvailabilitySlotRepository.findById(slotId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy slot rảnh"));
        validateMentorOwnsSlot(mentorUserId, slot);

        if (slot.isBooked()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Không thể sửa đổi slot rảnh đã được đặt lịch");
        }

        LocalDateTime start = request.startTime();
        LocalDateTime end = request.endTime();

        if (start == null || end == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thời gian bắt đầu và kết thúc là bắt buộc");
        }
        if (!end.isAfter(start)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thời gian kết thúc phải sau thời gian bắt đầu");
        }
        if (!start.isAfter(now())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Không thể sửa slot rảnh thành thời gian quá khứ");
        }

        if (mentorAvailabilitySlotRepository.existsOverlappingActiveSlotExcludeSelf(mentorUserId, slotId, start, end)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Khung giờ này đã bị trùng lặp với lịch rảnh khác của bạn");
        }

        MentorAvailabilityRule rule = slot.getRule();
        if (rule != null) {
            rule.setEffectiveFrom(start.toLocalDate());
            rule.setEffectiveTo(start.toLocalDate());
            rule.setStartTime(start.toLocalTime());
            rule.setEndTime(end.toLocalTime());
            rule.setNote(trimToNull(request.note()));
            mentorAvailabilityRuleRepository.save(rule);
        }

        slot.setStartTime(start);
        slot.setEndTime(end);
        slot.setNote(trimToNull(request.note()));

        availabilitySlotServiceRepository.deleteBySlotId(slotId);
        slot.getSlotServices().clear();

        List<MentorService> services;
        if (request.serviceIds() == null || request.serviceIds().isEmpty()) {
            services = mentorServiceRepository.findByMentorProfileUserIdAndIsActiveTrueOrderByCreatedAtAsc(mentorUserId);
        } else {
            services = mentorServiceRepository.findAllById(request.serviceIds());
            for (MentorService service : services) {
                if (service.getMentorProfile() == null || !service.getMentorProfile().getUserId().equals(mentorUserId) || !service.isActive()) {
                    throw new BaseException(ErrorCode.ACCESS_DENIED, "Dịch vụ không hợp lệ hoặc không thuộc về bạn");
                }
            }
        }

        if (!services.isEmpty()) {
            List<AvailabilitySlotService> slotServices = services.stream()
                    .map(service -> buildSlotServiceBinding(slot, service))
                    .toList();
            replaceSlotServices(slot, slotServices);
        }

        MentorAvailabilitySlot updatedSlot = mentorAvailabilitySlotRepository.save(slot);
        return toManagedSlotResponse(updatedSlot);
    }

    @Transactional
    public void deleteSlotDirectly(UUID mentorUserId, UUID slotId) {
        requireUserId(mentorUserId);
        if (slotId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã slot không hợp lệ");
        }

        MentorAvailabilitySlot slot = mentorAvailabilitySlotRepository.findById(slotId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy slot rảnh"));
        validateMentorOwnsSlot(mentorUserId, slot);

        if (slot.isBooked()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Không thể xóa slot rảnh đã được đặt lịch");
        }

        List<Booking> pendingBookings = bookingRepository.findBySlotIdAndStatus(slotId, BookingStatus.PENDING);
        if (!pendingBookings.isEmpty()) {
            for (Booking booking : pendingBookings) {
                booking.setStatus(BookingStatus.REJECTED);
                booking.setRejectedAt(now());
                booking.setRejectReason(RULE_DELETED_PENDING_REJECTION_REASON);
                bookingRepository.save(booking);

                try {
                    notificationService.createNotification(
                            booking.getMentee().getId(),
                            NotificationType.BOOKING_REJECTED,
                            "Yêu cầu đặt lịch đã bị hủy",
                            "Khung giờ rảnh của mentor đã được gỡ bỏ khỏi lịch biểu.",
                            "booking",
                            booking.getId()
                    );
                } catch (Exception ex) {
                    log.error("Failed to send booking rejection notification for booking: {}", booking.getId(), ex);
                }
            }
        }

        slot.setActive(false);
        mentorAvailabilitySlotRepository.save(slot);

        MentorAvailabilityRule rule = slot.getRule();
        if (rule != null) {
            rule.setActive(false);
            mentorAvailabilityRuleRepository.save(rule);
        }
    }

    @Transactional(readOnly = true)
    public List<MentorManagedAvailabilitySlotResponse> getMySlots(UUID mentorUserId, LocalDate fromDate, LocalDate toDate) {
        requireUserId(mentorUserId);
        LocalDateTime start;
        LocalDateTime end;

        if (fromDate == null) {
            LocalDate today = LocalDate.now(APP_ZONE);
            LocalDate mondayThisWeek = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            start = mondayThisWeek.atStartOfDay();
            end = mondayThisWeek.plusDays(14).atTime(LocalTime.MAX);
        } else {
            start = fromDate.atStartOfDay();
            end = toDate != null ? toDate.atTime(LocalTime.MAX) : start.toLocalDate().plusDays(14).atTime(LocalTime.MAX);
        }

        return mentorAvailabilitySlotRepository.findMyManagedSlotsWithServices(mentorUserId, start, end).stream()
                .map(this::toManagedSlotResponse)
                .toList();
    }

    @Transactional
    public MentorManagedAvailabilitySlotResponse replaceSlotServices(
            UUID mentorUserId,
            UUID slotId,
            ReplaceAvailabilitySlotServicesRequest request
    ) {
        throw new BaseException(ErrorCode.BAD_REQUEST, "API này đã lỗi thời và không còn được hỗ trợ. Hãy sử dụng API slots mới.");
    }

    private MentorManagedAvailabilitySlotResponse toManagedSlotResponse(MentorAvailabilitySlot slot) {
        List<AvailabilitySlotServiceBasicResponse> services = slot.getSlotServices().stream()
                .map(ss -> new AvailabilitySlotServiceBasicResponse(
                        ss.getService().getId(),
                        ss.getService().getTitle(),
                        ss.getService().getDurationMinutes(),
                        ss.getService().isFree(),
                        ss.getService().getPriceScoin()
                ))
                .collect(Collectors.toList());
        return new MentorManagedAvailabilitySlotResponse(
                slot.getId(),
                slot.getStartTime(),
                slot.getEndTime(),
                slot.getTimezone(),
                slot.isActive(),
                slot.isBooked(),
                slot.getNote(),
                services
        );
    }

    @Transactional(readOnly = true)
    public List<MentorAvailabilitySlotResponse> getAvailableSlots(MentorProfile mentorProfile, LocalDate fromDate, LocalDate toDate) {
        if (mentorProfile == null || mentorProfile.getUserId() == null) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy mentor");
        }

        AvailabilityCalendarWindowCalculator.DateRange dateRange = calendarWindowCalculator.resolveClientQueryRange(
                LocalDate.now(APP_ZONE),
                fromDate,
                toDate
        );
        LocalDateTime fromTime = max(dateRange.startDate().atStartOfDay(), now());
        LocalDateTime toTimeExclusive = dateRange.endDate().plusDays(1).atStartOfDay();

        List<MentorAvailabilitySlot> slots = mentorAvailabilitySlotRepository.findVisibleSlotsByMentorUserId(
                mentorProfile.getUserId(),
                fromTime,
                toTimeExclusive
        );
        if (slots.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<AvailabilitySlotService>> servicesBySlot = availabilitySlotServiceRepository.findBySlotIdInOrderByCreatedAtAsc(
                        slots.stream().map(MentorAvailabilitySlot::getId).toList()
                ).stream()
                .collect(Collectors.groupingBy(slotService -> slotService.getSlot().getId(), LinkedHashMap::new, Collectors.toList()));

        return slots.stream()
                .filter(slot -> servicesBySlot.containsKey(slot.getId()) && !servicesBySlot.get(slot.getId()).isEmpty())
                .map(slot -> toPublicSlotResponse(slot, mentorProfile.getTeachingMode(), servicesBySlot.getOrDefault(slot.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ServiceSlotCandidatesResponse getServiceSlotCandidates(UUID mentorUserId, UUID slotId, UUID serviceId) {
        if (mentorUserId == null || slotId == null || serviceId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "mentorUserId, slotId và serviceId là bắt buộc");
        }

        MentorAvailabilitySlot slot = resolvePublicActiveSlot(mentorUserId, slotId);

        AvailabilitySlotService slotService = availabilitySlotServiceRepository.findBySlotIdAndServiceId(slotId, serviceId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Service chưa được gắn vào availability slot này"));

        MentorService service = slotService.getService();
        if (!service.isActive()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Service hiện không còn hoạt động");
        }

        List<ServiceSlotCandidateItemResponse> candidates = buildSegmentCandidates(slot, service);
        return ServiceSlotCandidatesResponse.builder()
                .slotId(slotId)
                .serviceId(serviceId)
                .serviceDurationMinutes(service.getDurationMinutes())
                .candidateServiceSlots(candidates)
                .build();
    }

    @Async("slotGenerationExecutor")
    @Transactional
    public void generateSlotsForMentorAsync(UUID mentorUserId, LocalDate fromDate, LocalDate toDate) {
        try {
            MentorProfile mentorProfile = getManagedActiveMentorProfile(mentorUserId);
            generateSlotsForDateRange(mentorProfile, fromDate, toDate);
        } catch (Exception exception) {
            log.error(
                    "Failed to generate mentor availability windows asynchronously for mentor {} from {} to {}",
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

        for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
            List<MentorAvailabilityRule> openRules = matchingRulesForDate(rules, date, AvailabilityRuleType.OPEN);
            if (openRules.isEmpty()) {
                continue;
            }
            List<MentorAvailabilityRule> closedRules = matchingRulesForDate(rules, date, AvailabilityRuleType.CLOSED);
            for (MentorAvailabilityRule openRule : openRules) {
                generateWindowForOpenRule(mentorProfile, openRule, closedRules, date);
            }
        }
    }

    private List<ServiceSlotCandidateItemResponse> buildSegmentCandidates(MentorAvailabilitySlot slot, MentorService service) {
        validateSlotSegmentBase(slot, service);

        List<Booking> acceptedBookings = bookingRepository.findBySlotIdAndStatusOrderBySelectedStartTimeAsc(
                slot.getId(),
                BookingStatus.ACCEPTED
        );
        Map<String, Integer> pendingCountBySegment = toPendingSegmentCountMap(
                bookingRepository.countPendingSegmentsBySlotId(slot.getId(), BookingStatus.PENDING)
        );

        int durationMinutes = service.getDurationMinutes();
        LocalDateTime current = slot.getStartTime();
        LocalDateTime now = now();
        List<ServiceSlotCandidateItemResponse> results = new ArrayList<>();

        while (!current.plusMinutes(durationMinutes).isAfter(slot.getEndTime())) {
            LocalDateTime candidateStart = current;
            LocalDateTime candidateEnd = current.plusMinutes(durationMinutes);
            int pendingCount = pendingCountBySegment.getOrDefault(segmentKey(candidateStart, candidateEnd), 0);

            Booking blockingAcceptedBooking = acceptedBookings.stream()
                    .filter(booking -> overlaps(candidateStart, candidateEnd, selectedStartTime(booking), selectedEndTime(booking)))
                    .findFirst()
                    .orElse(null);
            boolean blockedByAccepted = blockingAcceptedBooking != null;
            UUID blockingServiceId = blockingAcceptedBooking != null && blockingAcceptedBooking.getService() != null
                    ? blockingAcceptedBooking.getService().getId()
                    : null;
            String blockingServiceTitle = blockingAcceptedBooking != null
                    ? firstNonBlank(
                    blockingAcceptedBooking.getServiceTitleSnapshot(),
                    blockingAcceptedBooking.getService() == null ? null : blockingAcceptedBooking.getService().getTitle()
            )
                    : null;
            boolean blockedBySameService = blockedByAccepted && blockingServiceId != null && blockingServiceId.equals(service.getId());
            boolean blockedByDifferentService = blockedByAccepted && !blockedBySameService;

            String reasonIfBlocked = null;
            String bookingConflictNote = null;
            boolean selectable = true;
            if (!candidateStart.isAfter(now)) {
                selectable = false;
                reasonIfBlocked = BLOCKED_BY_PAST_TIME_REASON;
            } else if (blockedByAccepted) {
                selectable = false;
                reasonIfBlocked = BLOCKED_BY_ACCEPTED_REASON;
                bookingConflictNote = blockedBySameService
                        ? "Segment này đã có booking ACCEPTED của cùng service"
                        : "Segment này đã có booking ACCEPTED của service khác trong cùng slot";
            } else if (pendingCount >= BookingQueueConstants.MAX_PENDING_REQUESTS_PER_SLOT) {
                selectable = false;
                reasonIfBlocked = BLOCKED_BY_PENDING_QUOTA_REASON;
            }

            results.add(ServiceSlotCandidateItemResponse.builder()
                    .startTime(candidateStart)
                    .endTime(candidateEnd)
                    .pendingCount(pendingCount)
                    .remainingPendingQuota(Math.max(0, BookingQueueConstants.MAX_PENDING_REQUESTS_PER_SLOT - pendingCount))
                    .isSelectable(selectable)
                    .reasonIfBlocked(reasonIfBlocked)
                    .blockedByAcceptedBooking(blockedByAccepted)
                    .blockingBookingId(blockingAcceptedBooking == null ? null : blockingAcceptedBooking.getId())
                    .blockingServiceId(blockingServiceId)
                    .blockingServiceTitle(blockingServiceTitle)
                    .blockedBySameService(blockedBySameService)
                    .blockedByDifferentService(blockedByDifferentService)
                    .bookingConflictNote(bookingConflictNote)
                    .build());
            current = candidateEnd;
        }

        return results;
    }

    private MentorAvailabilitySlot resolvePublicActiveSlot(UUID mentorUserId, UUID slotId) {
        MentorAvailabilitySlot slot = mentorAvailabilitySlotRepository.findById(slotId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy availability slot"));
        if (slot.getMentorProfile() == null || !mentorUserId.equals(slot.getMentorProfile().getUserId())) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Availability slot không thuộc về mentor này");
        }
        if (!slot.isActive()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Availability slot hiện không còn hoạt động");
        }
        return slot;
    }

    private void validateSlotSegmentBase(MentorAvailabilitySlot slot, MentorService service) {
        if (slot == null || slot.getStartTime() == null || slot.getEndTime() == null || !slot.getEndTime().isAfter(slot.getStartTime())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Availability slot không hợp lệ");
        }
        if (service == null || service.getDurationMinutes() == null || service.getDurationMinutes() <= 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Service không hợp lệ");
        }
        long slotMinutes = Duration.between(slot.getStartTime(), slot.getEndTime()).toMinutes();
        if (service.getDurationMinutes() > slotMinutes) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Service có thời lượng lớn hơn availability slot");
        }
    }

    private Map<String, Integer> toPendingSegmentCountMap(List<BookingSegmentPendingCountProjection> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> result = new HashMap<>();
        for (BookingSegmentPendingCountProjection row : rows) {
            if (row.getStartTime() == null || row.getEndTime() == null) {
                continue;
            }
            result.put(segmentKey(row.getStartTime(), row.getEndTime()), Math.toIntExact(row.getPendingCount()));
        }
        return result;
    }

    private String segmentKey(LocalDateTime startTime, LocalDateTime endTime) {
        return startTime + "|" + endTime;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private MentorAvailabilitySlotResponse toPublicSlotResponse(
            MentorAvailabilitySlot slot,
            TeachingMode teachingMode,
            List<AvailabilitySlotService> slotServices
    ) {
        Map<String, Integer> pendingCounts = toPendingSegmentCountMap(
                bookingRepository.countPendingSegmentsBySlotId(slot.getId(), BookingStatus.PENDING)
        );
        int totalPendingRequests = pendingCounts.values().stream().mapToInt(Integer::intValue).sum();
        int acceptedSlotCount = Math.toIntExact(bookingRepository.countBySlotIdAndStatus(slot.getId(), BookingStatus.ACCEPTED));
        Integer remainingRequestSlots = pendingCounts.isEmpty()
                ? BookingQueueConstants.MAX_PENDING_REQUESTS_PER_SLOT
                : Math.max(
                0,
                BookingQueueConstants.MAX_PENDING_REQUESTS_PER_SLOT - pendingCounts.values().stream().max(Integer::compareTo).orElse(0)
        );

        return MentorAvailabilitySlotResponse.builder()
                .slotId(slot.getId())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .timezone(slot.getTimezone())
                .durationMinutes((int) Duration.between(slot.getStartTime(), slot.getEndTime()).toMinutes())
                .teachingMode(teachingMode)
                .pendingRequestCount(totalPendingRequests)
                .acceptedSlotCount(acceptedSlotCount)
                .maxPendingRequests(BookingQueueConstants.MAX_PENDING_REQUESTS_PER_SLOT)
                .remainingRequestSlots(remainingRequestSlots)
                .services(slotServices.stream()
                        .map(AvailabilitySlotService::getService)
                        .filter(MentorService::isActive)
                        .map(this::toSlotServiceBasicResponse)
                        .toList())
                .build();
    }

    private AvailabilitySlotServiceBasicResponse toSlotServiceBasicResponse(MentorService service) {
        return AvailabilitySlotServiceBasicResponse.builder()
                .serviceId(service.getId())
                .title(service.getTitle())
                .durationMinutes(service.getDurationMinutes())
                .isFree(service.isFree())
                .priceScoin(service.getPriceScoin())
                .build();
    }

    private void validateMentorOwnsSlot(UUID mentorUserId, MentorAvailabilitySlot slot) {
        if (slot.getMentorProfile() == null || !mentorUserId.equals(slot.getMentorProfile().getUserId())) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền cập nhật availability slot này");
        }
    }

    private void generatePlanningWindowsForRule(MentorProfile mentorProfile, MentorAvailabilityRule rule) {
        if (!rule.isActive()) {
            return;
        }
        LocalDate today = LocalDate.now(APP_ZONE);
        List<AvailabilityCalendarWindowCalculator.DateRange> planningRanges = new ArrayList<>();
        planningRanges.add(calendarWindowCalculator.currentVisibleRange(today));
        if (calendarWindowCalculator.shouldPrepareNextCycle(today)) {
            planningRanges.add(calendarWindowCalculator.nextPreparationRange(today));
        }

        for (AvailabilityCalendarWindowCalculator.DateRange range : planningRanges) {
            generateRuleWindowsForRange(mentorProfile, rule, range.startDate(), range.endDate());
        }
    }

    private void generateRuleWindowsForRange(
            MentorProfile mentorProfile,
            MentorAvailabilityRule rule,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        List<MentorAvailabilityRule> activeRules = mentorAvailabilityRuleRepository.findActiveRulesOverlapping(
                mentorProfile.getUserId(),
                fromDate,
                toDate
        );
        List<MentorAvailabilityRule> closedRules = activeRules.stream()
                .filter(activeRule -> activeRule.getRuleType() == AvailabilityRuleType.CLOSED)
                .toList();

        for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
            if (!appliesOnDate(rule, date)) {
                continue;
            }
            generateWindowForOpenRule(mentorProfile, rule, closedRules, date);
        }
    }

    private void generateWindowForOpenRule(
            MentorProfile mentorProfile,
            MentorAvailabilityRule openRule,
            List<MentorAvailabilityRule> closedRules,
            LocalDate date
    ) {
        LocalDateTime start = LocalDateTime.of(date, openRule.getStartTime());
        LocalDateTime end = LocalDateTime.of(date, openRule.getEndTime());
        if (!shouldCreateWindow(mentorProfile.getUserId(), start, end, closedRules)) {
            return;
        }

        try {
            MentorAvailabilitySlot savedSlot = mentorAvailabilitySlotRepository.save(MentorAvailabilitySlot.builder()
                    .mentorProfile(mentorProfile)
                    .rule(openRule)
                    .startTime(start)
                    .endTime(end)
                    .timezone(APP_TIMEZONE)
                    .isActive(true)
                    .isBooked(false)
                    .recurrenceRule(openRule.getRepeatType().name())
                    .build());
            attachAllActiveServicesForGeneratedSlot(savedSlot);
        } catch (DataIntegrityViolationException exception) {
            log.warn(
                    "Skipped overlapping availability window for mentor {} at {} - {} due to database constraint",
                    mentorProfile.getUserId(),
                    start,
                    end
            );
        }
    }

    private boolean shouldCreateWindow(
            UUID mentorUserId,
            LocalDateTime start,
            LocalDateTime end,
            List<MentorAvailabilityRule> closedRules
    ) {
        return start.isAfter(now())
                && !overlapsClosedRule(start, end, closedRules)
                && !mentorAvailabilitySlotRepository.existsOverlappingActiveSlot(mentorUserId, start, end);
    }

    private void attachAllActiveServicesForGeneratedSlot(MentorAvailabilitySlot slot) {
        if (slot == null || slot.getMentorProfile() == null || slot.getMentorProfile().getUserId() == null) {
            return;
        }
        List<MentorService> services = mentorServiceRepository.findByMentorProfileUserIdAndIsActiveTrueOrderByCreatedAtAsc(
                slot.getMentorProfile().getUserId()
        );
        if (services.isEmpty()) {
            return;
        }
        replaceSlotServices(
                slot,
                services.stream()
                        .map(service -> buildSlotServiceBinding(slot, service))
                        .toList()
        );
    }

    private AvailabilitySlotService buildSlotServiceBinding(MentorAvailabilitySlot slot, MentorService service) {
        if (slot == null || slot.getId() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Availability slot chưa sẵn sàng để gắn service");
        }
        if (service == null || service.getId() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Service chưa sẵn sàng để gắn vào slot");
        }
        return AvailabilitySlotService.builder()
                .id(new AvailabilitySlotServiceId(slot.getId(), service.getId()))
                .slot(slot)
                .service(service)
                .build();
    }

    private void replaceSlotServices(MentorAvailabilitySlot slot, List<AvailabilitySlotService> bindings) {
        if (slot.getSlotServices() == null) {
            slot.setSlotServices(new java.util.LinkedHashSet<>());
        } else {
            slot.getSlotServices().clear();
        }
        slot.getSlotServices().addAll(bindings);
    }

    private boolean overlapsClosedRule(LocalDateTime windowStart, LocalDateTime windowEnd, List<MentorAvailabilityRule> closedRules) {
        for (MentorAvailabilityRule closedRule : closedRules) {
            if (closedRule.getStartTime() == null || closedRule.getEndTime() == null) {
                return true;
            }
            LocalDateTime closedStart = LocalDateTime.of(windowStart.toLocalDate(), closedRule.getStartTime());
            LocalDateTime closedEnd = LocalDateTime.of(windowStart.toLocalDate(), closedRule.getEndTime());
            if (windowStart.isBefore(closedEnd) && windowEnd.isAfter(closedStart)) {
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

    private void reconcileFutureWindowsForRule(MentorAvailabilityRule rule, String pendingRejectionReason) {
        LocalDateTime currentTime = now();
        List<MentorAvailabilitySlot> futureWindows = mentorAvailabilitySlotRepository.findByRuleIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(
                rule.getId(),
                currentTime
        );

        for (MentorAvailabilitySlot window : futureWindows) {
            if (!window.isActive()) {
                continue;
            }
            if (bookingRepository.existsOverlappingBySlotIdAndStatus(
                    window.getId(),
                    BookingStatus.ACCEPTED,
                    window.getStartTime(),
                    window.getEndTime()
            )) {
                continue;
            }

            rejectPendingBookingsForWindow(window.getId(), pendingRejectionReason, currentTime);
            window.setBooked(false);
            window.setActive(false);
        }
    }

    private void rejectPendingBookingsForWindow(UUID windowId, String reason, LocalDateTime currentTime) {
        List<Booking> pendingBookings = bookingRepository.findBySlotIdAndStatus(windowId, BookingStatus.PENDING);
        if (pendingBookings.isEmpty()) {
            return;
        }

        for (Booking pendingBooking : pendingBookings) {
            pendingBooking.setStatus(BookingStatus.REJECTED);
            pendingBooking.setRejectedAt(currentTime);
            pendingBooking.setRejectReason(reason);
        }
        bookingRepository.saveAll(pendingBookings);
        for (Booking pendingBooking : pendingBookings) {
            notificationService.createNotification(
                    pendingBooking.getMentee().getId(),
                    NotificationType.BOOKING_AUTO_REJECTED,
                    "Yêu cầu đặt lịch không còn hiệu lực",
                    "Yêu cầu đặt lịch của bạn đã bị từ chối: " + reason,
                    "BOOKING",
                    pendingBooking.getId()
            );
        }
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
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private LocalTime normalizedStartTime(UpsertAvailabilityRuleRequest request) {
        return request.ruleType() == AvailabilityRuleType.CLOSED && request.startTime() == null ? null : request.startTime();
    }

    private LocalTime normalizedEndTime(UpsertAvailabilityRuleRequest request) {
        return request.ruleType() == AvailabilityRuleType.CLOSED && request.endTime() == null ? null : request.endTime();
    }

    private LocalDateTime selectedStartTime(Booking booking) {
        return booking.getSelectedStartTime();
    }

    private LocalDateTime selectedEndTime(Booking booking) {
        return booking.getSelectedEndTime();
    }

    private boolean overlaps(LocalDateTime start1, LocalDateTime end1, LocalDateTime start2, LocalDateTime end2) {
        return start1 != null
                && end1 != null
                && start2 != null
                && end2 != null
                && start1.isBefore(end2)
                && end1.isAfter(start2);
    }

    private LocalDateTime now() {
        return DateTimeUtil.now();
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
}
