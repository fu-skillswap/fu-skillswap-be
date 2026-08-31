package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.booking.constant.BookingQueueConstants;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRepeatType;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRuleType;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilitySlotService;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionCommand;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionExecutor;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilityRule;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateAvailabilitySlotRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.DeactivateAvailabilitySlotRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.ReplaceAvailabilitySlotServicesRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.UpdateAvailabilitySlotRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.UpsertAvailabilityRuleRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.AvailabilityRuleResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.AvailabilitySlotServiceBasicResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.MentorManagedAvailabilitySlotResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.SlotMutationCapabilityResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.SlotMutationMode;
import com.fptu.exe.skillswap.modules.booking.repository.AvailabilitySlotServiceRepository;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilityRuleRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.booking.repository.projection.BookingSegmentPendingCountProjection;
import com.fptu.exe.skillswap.modules.booking.support.AvailabilityCalendarWindowCalculator;
import com.fptu.exe.skillswap.modules.mentor.dto.response.ServiceSlotCandidateItemResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.ServiceSlotCandidatesResponse;
import com.fptu.exe.skillswap.modules.mentor.port.EffectiveBookingPolicy;
import com.fptu.exe.skillswap.modules.mentor.port.MentorBookingCapability;
import com.fptu.exe.skillswap.modules.mentor.port.MentorBookingQueryPort;
import com.fptu.exe.skillswap.modules.mentor.port.MentorPublicAvailability;
import com.fptu.exe.skillswap.modules.mentor.port.ServiceSlotCandidate;
import com.fptu.exe.skillswap.modules.notification.port.NotificationCommandPort;
import com.fptu.exe.skillswap.modules.payment.service.PricingPolicy;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.VersionConflictException;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MentorAvailabilityService {

    private static final String APP_TIMEZONE = "Asia/Ho_Chi_Minh";
    private static final ZoneId APP_ZONE = ZoneId.of(APP_TIMEZONE);
    private static final String RULE_UPDATED_PENDING_REJECTION_REASON = "Mentor đã thay đổi lịch rảnh";
    private static final String RULE_DELETED_PENDING_REJECTION_REASON = "Mentor đã hủy lịch rảnh";
    private static final String BLOCKED_BY_ACCEPTED_REASON = "Đã có booking được mentor chấp nhận trùng với khoảng thời gian này";
    private static final String BLOCKED_BY_PENDING_QUOTA_REASON = "Segment này đã đạt tối đa 3 yêu cầu chờ xác nhận";
    private static final String BLOCKED_BY_PAST_TIME_REASON = "Segment này đã bắt đầu hoặc đã trôi qua";
    private static final String BLOCKED_BY_LEAD_TIME_REASON = "Yêu cầu đặt trước tối thiểu";
    private static final String BLOCKED_BY_HORIZON_REASON = "Vượt quá thời hạn mở lịch cho phép";
    private static final int MAXIMUM_PARENT_SLOT_DURATION_MINUTES = 720;
    private TimeProvider timeProvider = TimeProvider.from(Clock.systemUTC());

    @Autowired(required = false)
    void setTimeProvider(TimeProvider timeProvider) {
        if (timeProvider != null) {
            this.timeProvider = timeProvider;
        }
    }
    private static final List<BookingStatus> SLOT_LOCKING_STATUSES = List.of(
            BookingStatus.ACCEPTED_AWAITING_PAYMENT,
            BookingStatus.PAID
    );

    private final MentorBookingQueryPort mentorBookingQueryPort;
    private final MentorAvailabilityRuleRepository mentorAvailabilityRuleRepository;
    private final MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;
    private final AvailabilitySlotServiceRepository availabilitySlotServiceRepository;
    private final BookingRepository bookingRepository;
    private final NotificationCommandPort notificationCommandPort;
    private final AvailabilityCalendarWindowCalculator calendarWindowCalculator;
    private final PaymentProperties paymentProperties;

    private AvailabilityTemplateService availabilityTemplateService;

    @Autowired(required = false)
    void setAvailabilityTemplateService(AvailabilityTemplateService availabilityTemplateService) {
        this.availabilityTemplateService = availabilityTemplateService;
    }

    @Autowired
    public MentorAvailabilityService(
            MentorBookingQueryPort mentorBookingQueryPort,
            MentorAvailabilityRuleRepository mentorAvailabilityRuleRepository,
            MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository,
            AvailabilitySlotServiceRepository availabilitySlotServiceRepository,
            BookingRepository bookingRepository,
            NotificationCommandPort notificationCommandPort,
            AvailabilityCalendarWindowCalculator calendarWindowCalculator,
            PaymentProperties paymentProperties
    ) {
        this.mentorBookingQueryPort = mentorBookingQueryPort;
        this.mentorAvailabilityRuleRepository = mentorAvailabilityRuleRepository;
        this.mentorAvailabilitySlotRepository = mentorAvailabilitySlotRepository;
        this.availabilitySlotServiceRepository = availabilitySlotServiceRepository;
        this.bookingRepository = bookingRepository;
        this.notificationCommandPort = notificationCommandPort;
        this.calendarWindowCalculator = calendarWindowCalculator;
        this.paymentProperties = paymentProperties;
    }

    @Transactional
    public MentorManagedAvailabilitySlotResponse createSlotDirectly(UUID mentorUserId, CreateAvailabilitySlotRequest request) {
        requireUserId(mentorUserId);
        validateManagedActiveMentor(mentorUserId);

        LocalDateTime start = toUtcLocal(request.legacyJavaBridge() ? request.startAt() : normalizeToWholeMinute(request.startAt()));
        LocalDateTime end = toUtcLocal(request.legacyJavaBridge() ? request.endAt() : normalizeToWholeMinute(request.endAt()));

        if (start == null || end == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thời gian bắt đầu và kết thúc là bắt buộc");
        }
        if (!end.isAfter(start)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thời gian kết thúc phải sau thời gian bắt đầu");
        }
        validateParentSlotDuration(start, end);
        if (!start.isAfter(now())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Không thể tạo slot rảnh ở quá khứ");
        }

        if (availabilityTemplateService != null) {
            availabilityTemplateService.replaceGeneratedOccurrences(mentorUserId, start, end,
                    Boolean.TRUE.equals(request.replaceGeneratedOccurrences()), Boolean.TRUE.equals(request.rejectPendingBookings()),
                    request.expectedTemplateVersions());
        }
        if (mentorAvailabilitySlotRepository.existsOverlappingActiveSlot(mentorUserId, toInstant(start), toInstant(end))) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Khung giờ này đã bị trùng lặp với lịch rảnh khác của bạn");
        }

        MentorAvailabilitySlot slot = MentorAvailabilitySlot.builder()
                .mentorUserId(mentorUserId)
                .startTime(start)
                .endTime(end)
                .timezone(APP_TIMEZONE)
                .isActive(true)
                .isBooked(false)
                .recurrenceRule(AvailabilityRepeatType.NONE.name())
                .note(trimToNull(request.note()))
                .build();
        MentorAvailabilitySlot savedSlot = mentorAvailabilitySlotRepository.save(slot);

        List<ServiceSlotCandidate> services = resolveManagedServices(mentorUserId, request.serviceIds());

        if (!services.isEmpty()) {
            List<AvailabilitySlotService> slotServices = services.stream()
                    .map(service -> AvailabilitySlotService.of(savedSlot, service.serviceId()))
                    .toList();
            replaceSlotServices(savedSlot, slotServices);
        }
        if (availabilityTemplateService != null) availabilityTemplateService.markMentorDue(mentorUserId);

        return toManagedSlotResponse(savedSlot);
    }

    @Transactional
    public MentorManagedAvailabilitySlotResponse updateSlotDirectly(UUID mentorUserId, UUID slotId, UpdateAvailabilitySlotRequest request) {
        requireUserId(mentorUserId);
        if (slotId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã slot không hợp lệ");
        }

        MentorAvailabilitySlot slot = findSlotForUpdateOrLegacyRead(slotId);
        validateMentorOwnsSlot(mentorUserId, slot);
        if (slot.getTemplate() != null) {
            throw new BaseException(ErrorCode.GENERATED_SLOT_MANAGED_BY_TEMPLATE);
        }
        if (!Objects.equals(request.expectedVersion(), normalizedVersion(slot))) {
            throw slotVersionConflict(slotId, request.expectedVersion(), normalizedVersion(slot));
        }

        LocalDateTime start = toUtcLocal(request.legacyJavaBridge() ? request.startAt() : normalizeToWholeMinute(request.startAt()));
        LocalDateTime end = toUtcLocal(request.legacyJavaBridge() ? request.endAt() : normalizeToWholeMinute(request.endAt()));

        if (start == null || end == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thời gian bắt đầu và kết thúc là bắt buộc");
        }
        if (!end.isAfter(start)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thời gian kết thúc phải sau thời gian bắt đầu");
        }
        validateParentSlotDuration(start, end);
        if (!start.isAfter(now())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Không thể sửa slot rảnh thành thời gian quá khứ");
        }

        if (availabilityTemplateService != null) {
            availabilityTemplateService.replaceGeneratedOccurrences(mentorUserId, start, end,
                    Boolean.TRUE.equals(request.replaceGeneratedOccurrences()), Boolean.TRUE.equals(request.rejectPendingBookings()),
                    request.expectedTemplateVersions());
        }
        if (mentorAvailabilitySlotRepository.existsOverlappingActiveSlotExcludeSelf(mentorUserId, slotId, toInstant(start), toInstant(end))) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Khung giờ này đã bị trùng lặp với lịch rảnh khác của bạn");
        }

        LocalDateTime previousStart = slot.getStartTime();
        LocalDateTime previousEnd = slot.getEndTime();
        MentorAvailabilityRule rule = slot.getRule();
        if (rule != null) {
            rule.setEffectiveFrom(start.toLocalDate());
            rule.setEffectiveTo(start.toLocalDate());
            rule.setStartTime(start.toLocalTime());
            rule.setEndTime(end.toLocalTime());
            rule.setNote(trimToNull(request.note()));
            mentorAvailabilityRuleRepository.save(rule);
        }

        Set<UUID> oldServiceIds = slot.getSlotServices().stream().map(AvailabilitySlotService::getServiceId).collect(Collectors.toSet());
        Set<UUID> newServiceIds = new LinkedHashSet<>(request.serviceIds());
        boolean timeChanged = !start.equals(previousStart) || !end.equals(previousEnd);
        List<Booking> affectedPending = bookingRepository.findBySlotIdAndStatus(slotId, BookingStatus.PENDING).stream()
                .filter(booking -> selectedStartTime(booking) != null && selectedStartTime(booking).isAfter(now()))
                .filter(booking -> timeChanged || (booking.getServiceId() != null && !newServiceIds.contains(booking.getServiceId())))
                .toList();
        if (!affectedPending.isEmpty() && !Boolean.TRUE.equals(request.rejectPendingBookings())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "SLOT_HAS_PENDING_BOOKINGS");
        }
        if (slot.isBooked() && request.expectedVersion() == 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Slot đã có booking và không thể thay đổi theo legacy flow");
        }
        if (hasLockingBookings(slotId) && (timeChanged || !oldServiceIds.equals(newServiceIds))) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "SLOT_HAS_LOCKING_BOOKINGS");
        }
        rejectPendingBookings(affectedPending, RULE_UPDATED_PENDING_REJECTION_REASON);

        slot.setStartTime(start);
        slot.setEndTime(end);
        slot.setNote(trimToNull(request.note()));

        availabilitySlotServiceRepository.deleteBySlotId(slotId);
        slot.getSlotServices().clear();

        List<ServiceSlotCandidate> services = resolveManagedServices(mentorUserId, request.serviceIds());

        if (!services.isEmpty()) {
            List<AvailabilitySlotService> slotServices = services.stream()
                    .map(service -> AvailabilitySlotService.of(slot, service.serviceId()))
                    .toList();
            replaceSlotServices(slot, slotServices);
        }
        if (availabilityTemplateService != null) availabilityTemplateService.markMentorDue(mentorUserId);

        MentorAvailabilitySlot updatedSlot = mentorAvailabilitySlotRepository.save(slot);
        return toManagedSlotResponse(updatedSlot);
    }

    @Transactional
    public MentorManagedAvailabilitySlotResponse deactivateSlot(UUID mentorUserId, UUID slotId, DeactivateAvailabilitySlotRequest request) {
        requireUserId(mentorUserId);
        if (slotId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã slot không hợp lệ");
        }

        MentorAvailabilitySlot slot = findSlotForUpdateOrLegacyRead(slotId);
        validateMentorOwnsSlot(mentorUserId, slot);
        if (slot.getTemplate() != null) {
            if (request == null || request.expectedTemplateVersion() == null) {
                throw new BaseException(ErrorCode.AVAILABILITY_TEMPLATE_VERSION_CONFLICT,
                        "Availability template đã được cập nhật");
            }
            if (availabilityTemplateService == null) {
                throw new BaseException(ErrorCode.GENERATED_SLOT_MANAGED_BY_TEMPLATE);
            }
            availabilityTemplateService.deactivateGeneratedSlot(mentorUserId, slot, request.expectedTemplateVersion(),
                    Boolean.TRUE.equals(request.rejectPendingBookings()));
            return toManagedSlotResponse(slot);
        }
        if (!slot.isActive()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "SLOT_ALREADY_INACTIVE");
        }
        if (request == null || !Objects.equals(request.expectedVersion(), normalizedVersion(slot))) {
            throw slotVersionConflict(slotId, request == null ? null : request.expectedVersion(), normalizedVersion(slot));
        }

        List<Booking> pendingBookings = bookingRepository.findBySlotIdAndStatus(slotId, BookingStatus.PENDING).stream()
                .filter(booking -> selectedStartTime(booking) != null && selectedStartTime(booking).isAfter(now()))
                .toList();
        if (!pendingBookings.isEmpty() && !Boolean.TRUE.equals(request.rejectPendingBookings())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "SLOT_HAS_PENDING_BOOKINGS");
        }
        if (hasLockingBookings(slotId)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "SLOT_HAS_LOCKING_BOOKINGS");
        }
        rejectPendingBookings(pendingBookings, RULE_DELETED_PENDING_REJECTION_REASON);

        slot.setActive(false);
        mentorAvailabilitySlotRepository.save(slot);

        MentorAvailabilityRule rule = slot.getRule();
        if (rule != null) {
            rule.setActive(false);
            mentorAvailabilityRuleRepository.save(rule);
        }
        if (availabilityTemplateService != null) availabilityTemplateService.markMentorDue(mentorUserId);
        return toManagedSlotResponse(slot);
    }

    @Transactional(readOnly = true)
    public List<MentorManagedAvailabilitySlotResponse> getMySlots(UUID mentorUserId, Boolean isActive, LocalDate fromDate, LocalDate toDate) {
        requireUserId(mentorUserId);
        if (fromDate == null || toDate == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "fromDate và toDate là bắt buộc");
        }
        if (toDate.isBefore(fromDate) || java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate) + 1 > 31) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Khoảng ngày availability không hợp lệ hoặc vượt quá giới hạn 31 ngày");
        }
        LocalDateTime start = fromDate.atStartOfDay();
        LocalDateTime end = toDate.plusDays(1).atStartOfDay();

        return mentorAvailabilitySlotRepository.findMyManagedSlotsWithServices(mentorUserId, toInstant(start), toInstant(end)).stream()
                .filter(slot -> isActive == null || slot.isActive() == isActive)
                .map(this::toManagedSlotResponse)
                .toList();
    }

    private MentorManagedAvailabilitySlotResponse toManagedSlotResponse(MentorAvailabilitySlot slot) {
        Set<UUID> serviceIds = slot.getSlotServices().stream().map(AvailabilitySlotService::getServiceId).collect(Collectors.toSet());
        Map<UUID, ServiceSlotCandidate> servicesMap = mentorBookingQueryPort.getServicesByIds(serviceIds);
        List<AvailabilitySlotServiceBasicResponse> services = serviceIds.stream()
                .map(servicesMap::get)
                .filter(Objects::nonNull)
                .map(s -> new AvailabilitySlotServiceBasicResponse(
                        s.serviceId(),
                        s.title(),
                        s.durationMinutes(),
                        Boolean.TRUE.equals(s.isFree()),
                        normalizedServicePrice(s),
                        bindingRemovalCapability(slot, s.serviceId())
                ))
                .collect(Collectors.toList());

        return new MentorManagedAvailabilitySlotResponse(
                slot.getId(),
                toInstant(slot.getStartTime()),
                toInstant(slot.getEndTime()),
                slot.getTimezone(),
                slot.isActive(),
                slot.getNote(),
                services,
                slot.getVersion(),
                Math.toIntExact(bookingRepository.countBySlotIdAndStatus(slot.getId(), BookingStatus.PENDING)),
                Math.toIntExact(bookingRepository.countBySlotIdAndStatusIn(slot.getId(), SLOT_LOCKING_STATUSES)),
                hasLockingBookings(slot.getId()),
                timeMutationCapability(slot),
                deactivationCapability(slot),
                true
        );
    }

    @Transactional(readOnly = true)
    public List<MentorPublicAvailability> getAvailableSlots(UUID mentorUserId, LocalDate fromDate, LocalDate toDate) {
        if (mentorUserId == null) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy mentor");
        }
        MentorBookingCapability capability = mentorBookingQueryPort.getBookingCapability(mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy mentor"));
        return getAvailableSlots(capability, fromDate, toDate);
    }

    @Transactional(readOnly = true)
    public List<MentorPublicAvailability> getAvailableSlots(MentorBookingCapability mentorProfile, LocalDate fromDate, LocalDate toDate) {
        if (mentorProfile == null || mentorProfile.mentorUserId() == null) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy mentor");
        }

        LocalDateTime now = now();
        AvailabilityCalendarWindowCalculator.DateRange dateRange = calendarWindowCalculator.resolveClientQueryRange(
                LocalDate.now(APP_ZONE),
                fromDate,
                toDate
        );
        LocalDateTime fromTime = max(dateRange.startDate().atStartOfDay(), now);
        LocalDateTime toTimeExclusive = dateRange.endDate().plusDays(1).atStartOfDay();

        List<MentorAvailabilitySlot> slots = mentorAvailabilitySlotRepository.findVisibleSlotsByMentorUserId(
                mentorProfile.mentorUserId(),
                toInstant(fromTime),
                toInstant(toTimeExclusive)
        );
        if (slots.isEmpty()) {
            return List.of();
        }

        slots = slots.stream()
                .filter(slot -> availabilityTemplateService == null || availabilityTemplateService.isGeneratedSlotEligible(slot))
                .filter(slot -> isSlotEligibleByPolicy(slot, now))
                .toList();
        if (slots.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<AvailabilitySlotService>> servicesBySlot = availabilitySlotServiceRepository.findBySlotIdInOrderByCreatedAtAsc(
                        slots.stream().map(MentorAvailabilitySlot::getId).toList()
                ).stream()
                .collect(Collectors.groupingBy(slotService -> slotService.getSlot().getId(), LinkedHashMap::new, Collectors.toList()));

        Set<UUID> allServiceIds = servicesBySlot.values().stream()
                .flatMap(List::stream)
                .map(AvailabilitySlotService::getServiceId)
                .collect(Collectors.toSet());
        Map<UUID, ServiceSlotCandidate> activeCandidatesMap = mentorBookingQueryPort.getServicesByIds(allServiceIds);

        return slots.stream()
                .filter(slot -> servicesBySlot.containsKey(slot.getId())
                        && servicesBySlot.get(slot.getId()).stream().anyMatch(binding -> {
                            ServiceSlotCandidate c = activeCandidatesMap.get(binding.getServiceId());
                            return c != null && Boolean.TRUE.equals(c.active());
                        }))
                .map(slot -> toPublicSlotResponse(slot, servicesBySlot.getOrDefault(slot.getId(), List.of()), activeCandidatesMap))
                .toList();
    }

    private boolean isSlotEligibleByPolicy(MentorAvailabilitySlot slot, LocalDateTime now) {
        if (slot.getMentorUserId() == null) {
            return true;
        }
        EffectiveBookingPolicy policy = mentorBookingQueryPort.getEffectiveBookingPolicy(slot.getMentorUserId());
        int leadTimeMinutes = policy.minimumBookingLeadTimeMinutes();
        int horizonDays = policy.maximumBookingHorizonDays();
        LocalDateTime earliestAllowed = now.plusMinutes(leadTimeMinutes);
        LocalDateTime latestAllowed = now.plusDays(horizonDays);
        return slot.getEndTime().isAfter(earliestAllowed) && slot.getStartTime().isBefore(latestAllowed);
    }

    @Transactional(readOnly = true)
    public ServiceSlotCandidatesResponse getServiceSlotCandidates(UUID mentorUserId, UUID slotId, UUID serviceId) {
        if (mentorUserId == null || slotId == null || serviceId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "mentorUserId, slotId và serviceId là bắt buộc");
        }

        MentorAvailabilitySlot slot = resolvePublicActiveSlot(mentorUserId, slotId);

        availabilitySlotServiceRepository.findBySlotIdAndServiceId(slotId, serviceId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Service chưa được gắn vào availability slot này"));

        ServiceSlotCandidate service = mentorBookingQueryPort.getActiveServiceCandidate(serviceId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_CONFLICT, "Service hiện không còn hoạt động hoặc không thuộc mentor"));

        List<ServiceSlotCandidateItemResponse> candidates = buildSegmentCandidates(slot, service);
        return ServiceSlotCandidatesResponse.builder()
                .slotId(slotId)
                .serviceId(serviceId)
                .serviceDurationMinutes(service.durationMinutes())
                .candidateServiceSlots(candidates)
                .build();
    }

    @Async("slotGenerationExecutor")
    @Transactional
    public void generateSlotsForMentorAsync(UUID mentorUserId, LocalDate fromDate, LocalDate toDate) {
        try {
            validateManagedActiveMentor(mentorUserId);
            generateSlotsForDateRange(mentorUserId, fromDate, toDate);
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
    public void generateSlotsForDateRange(UUID mentorUserId, LocalDate fromDate, LocalDate toDate) {
        List<MentorAvailabilityRule> rules = mentorAvailabilityRuleRepository.findActiveRulesOverlapping(
                mentorUserId,
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
                generateWindowForOpenRule(mentorUserId, openRule, closedRules, date);
            }
        }
    }

    private List<ServiceSlotCandidateItemResponse> buildSegmentCandidates(MentorAvailabilitySlot slot, ServiceSlotCandidate service) {
        validateSlotSegmentBase(slot, service);

        UUID mentorUserId = slot.getMentorUserId();
        EffectiveBookingPolicy policy = mentorBookingQueryPort.getEffectiveBookingPolicy(mentorUserId);
        int leadTimeMinutes = policy.minimumBookingLeadTimeMinutes();
        int horizonDays = policy.maximumBookingHorizonDays();

        List<Booking> acceptedBookings = SLOT_LOCKING_STATUSES.stream()
                .flatMap(status -> bookingRepository.findBySlotIdAndStatusOrderBySelectedStartTimeAsc(slot.getId(), status).stream())
                .toList();

        Map<String, Integer> pendingCountBySegment = toPendingSegmentCountMap(
                bookingRepository.countPendingSegmentsBySlotId(slot.getId(), BookingStatus.PENDING)
        );

        int durationMinutes = service.durationMinutes();
        LocalDateTime current = slot.getStartTime();
        LocalDateTime now = now();
        LocalDateTime earliestAllowed = now.plusMinutes(leadTimeMinutes);
        LocalDateTime latestAllowed = now.plusDays(horizonDays);
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

            UUID blockingServiceId = blockingAcceptedBooking != null ? blockingAcceptedBooking.getServiceId() : null;
            String blockingServiceTitle = blockingAcceptedBooking != null ? blockingAcceptedBooking.getServiceTitleSnapshot() : null;
            boolean blockedBySameService = blockedByAccepted && blockingServiceId != null && blockingServiceId.equals(service.serviceId());
            boolean blockedByDifferentService = blockedByAccepted && !blockedBySameService;

            String reasonIfBlocked = null;
            String bookingConflictNote = null;
            boolean selectable = true;
            if (!candidateStart.isAfter(now)) {
                selectable = false;
                reasonIfBlocked = BLOCKED_BY_PAST_TIME_REASON;
                bookingConflictNote = "Khung giờ này đã bắt đầu hoặc đã trôi qua";
            } else if (candidateStart.isBefore(earliestAllowed)) {
                selectable = false;
                reasonIfBlocked = BLOCKED_BY_LEAD_TIME_REASON;
                String leadTimeText = (leadTimeMinutes >= 60 && leadTimeMinutes % 60 == 0)
                        ? (leadTimeMinutes / 60) + " giờ"
                        : leadTimeMinutes + " phút";
                bookingConflictNote = "Khung giờ này yêu cầu đặt trước tối thiểu " + leadTimeText;
            } else if (candidateStart.isAfter(latestAllowed)) {
                selectable = false;
                reasonIfBlocked = BLOCKED_BY_HORIZON_REASON;
                bookingConflictNote = "Chỉ nhận đặt lịch trong vòng " + horizonDays + " ngày tới";
            } else if (blockedByAccepted) {
                selectable = false;
                reasonIfBlocked = BLOCKED_BY_ACCEPTED_REASON;
                bookingConflictNote = blockedBySameService
                        ? "Segment này đã có booking đã được xác nhận của cùng service"
                        : "Segment này đã có booking đã được xác nhận của service khác trong cùng slot";
            } else if (pendingCount >= BookingQueueConstants.MAX_PENDING_REQUESTS_PER_SLOT) {
                selectable = false;
                reasonIfBlocked = BLOCKED_BY_PENDING_QUOTA_REASON;
                bookingConflictNote = "Segment này đã đạt tối đa 3 yêu cầu chờ xác nhận";
            }

            results.add(ServiceSlotCandidateItemResponse.builder()
                    .startTime(candidateStart)
                    .endTime(candidateEnd)
                    .startAt(BookingTime.toInstant(candidateStart))
                    .endAt(BookingTime.toInstant(candidateEnd))
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
        if (slot.getMentorUserId() == null || !mentorUserId.equals(slot.getMentorUserId())) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Availability slot không thuộc về mentor này");
        }
        if (!slot.isActive()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Availability slot hiện không còn hoạt động");
        }
        if (availabilityTemplateService != null && !availabilityTemplateService.isGeneratedSlotEligible(slot)) {
            throw new BaseException(ErrorCode.AVAILABILITY_TEMPLATE_OCCURRENCE_UNAVAILABLE);
        }
        return slot;
    }

    private void validateSlotSegmentBase(MentorAvailabilitySlot slot, ServiceSlotCandidate service) {
        if (slot == null || slot.getStartTime() == null || slot.getEndTime() == null || !slot.getEndTime().isAfter(slot.getStartTime())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Availability slot không hợp lệ");
        }
        if (service == null || service.durationMinutes() == null || service.durationMinutes() <= 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Service không hợp lệ");
        }
        long slotMinutes = Duration.between(slot.getStartTime(), slot.getEndTime()).toMinutes();
        if (service.durationMinutes() > slotMinutes) {
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

    private MentorPublicAvailability toPublicSlotResponse(
            MentorAvailabilitySlot slot,
            List<AvailabilitySlotService> slotServices,
            Map<UUID, ServiceSlotCandidate> candidatesMap
    ) {
        Map<String, Integer> pendingCounts = toPendingSegmentCountMap(
                bookingRepository.countPendingSegmentsBySlotId(slot.getId(), BookingStatus.PENDING)
        );
        int totalPendingRequests = pendingCounts.values().stream().mapToInt(Integer::intValue).sum();
        int acceptedSlotCount = Math.toIntExact(bookingRepository.countBySlotIdAndStatusIn(slot.getId(), SLOT_LOCKING_STATUSES));
        Integer remainingRequestSlots = pendingCounts.isEmpty()
                ? BookingQueueConstants.MAX_PENDING_REQUESTS_PER_SLOT
                : Math.max(
                0,
                BookingQueueConstants.MAX_PENDING_REQUESTS_PER_SLOT - pendingCounts.values().stream().max(Integer::compareTo).orElse(0)
        );

        List<ServiceSlotCandidate> serviceResponses = slotServices.stream()
                .map(s -> candidatesMap.get(s.getServiceId()))
                .filter(Objects::nonNull)
                .filter(s -> Boolean.TRUE.equals(s.active()))
                .toList();

        return new MentorPublicAvailability(slot.getId(), slot.getStartTime(), slot.getEndTime(), slot.getTimezone(),
                (int) Duration.between(slot.getStartTime(), slot.getEndTime()).toMinutes(), totalPendingRequests,
                acceptedSlotCount, BookingQueueConstants.MAX_PENDING_REQUESTS_PER_SLOT, remainingRequestSlots, serviceResponses);
    }

    private AvailabilitySlotServiceBasicResponse toSlotServiceBasicResponse(ServiceSlotCandidate service) {
        return AvailabilitySlotServiceBasicResponse.builder()
                .serviceId(service.serviceId())
                .title(service.title())
                .durationMinutes(service.durationMinutes())
                .isFree(Boolean.TRUE.equals(service.isFree()))
                .priceScoin(Boolean.TRUE.equals(service.isFree()) || service.priceScoin() == null || service.priceScoin() == 0 ? 0
                        : PricingPolicy.menteePayableScoin(service.priceScoin(), paymentProperties))
                .build();
    }

    private Integer normalizedServicePrice(ServiceSlotCandidate service) {
        if (service == null || Boolean.TRUE.equals(service.isFree())) {
            return 0;
        }
        return service.priceScoin() == null ? 0 : service.priceScoin();
    }

    private void validateMentorOwnsSlot(UUID mentorUserId, MentorAvailabilitySlot slot) {
        if (slot.getMentorUserId() == null || !mentorUserId.equals(slot.getMentorUserId())) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền cập nhật availability slot này");
        }
    }

    private void generatePlanningWindowsForRule(UUID mentorUserId, MentorAvailabilityRule rule) {
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
            generateRuleWindowsForRange(mentorUserId, rule, range.startDate(), range.endDate());
        }
    }

    private void generateRuleWindowsForRange(
            UUID mentorUserId,
            MentorAvailabilityRule rule,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        List<MentorAvailabilityRule> activeRules = mentorAvailabilityRuleRepository.findActiveRulesOverlapping(
                mentorUserId,
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
            generateWindowForOpenRule(mentorUserId, rule, closedRules, date);
        }
    }

    private void generateWindowForOpenRule(
            UUID mentorUserId,
            MentorAvailabilityRule openRule,
            List<MentorAvailabilityRule> closedRules,
            LocalDate date
    ) {
        LocalDateTime start = LocalDateTime.of(date, openRule.getStartTime());
        LocalDateTime end = LocalDateTime.of(date, openRule.getEndTime());
        if (!shouldCreateWindow(mentorUserId, start, end, closedRules)) {
            return;
        }

        try {
            MentorAvailabilitySlot savedSlot = mentorAvailabilitySlotRepository.save(MentorAvailabilitySlot.builder()
                    .mentorUserId(mentorUserId)
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
                    mentorUserId,
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
                && !mentorAvailabilitySlotRepository.existsOverlappingActiveSlot(mentorUserId, toInstant(start), toInstant(end));
    }

    private void attachAllActiveServicesForGeneratedSlot(MentorAvailabilitySlot slot) {
        if (slot == null || slot.getMentorUserId() == null) {
            return;
        }
        List<ServiceSlotCandidate> services = mentorBookingQueryPort.getActiveServicesForMentor(slot.getMentorUserId());
        if (services.isEmpty()) {
            return;
        }
        replaceSlotServices(
                slot,
                services.stream()
                        .map(service -> AvailabilitySlotService.of(slot, service.serviceId()))
                        .toList()
        );
    }

    private void replaceSlotServices(MentorAvailabilitySlot slot, List<AvailabilitySlotService> bindings) {
        if (slot.getSlotServices() == null) {
            slot.setSlotServices(new LinkedHashSet<>());
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
        List<MentorAvailabilitySlot> futureWindows = mentorAvailabilitySlotRepository.findByRuleIdAndStartTimeUtcGreaterThanEqualOrderByStartTimeUtcAsc(
                rule.getId(),
                toInstant(currentTime)
        );

        for (MentorAvailabilitySlot window : futureWindows) {
            if (!window.isActive()) {
                continue;
            }
            Instant startUtc = window.getStartTimeUtc() != null ? window.getStartTimeUtc()
                    : (window.getStartTime() == null ? null : toInstant(window.getStartTime()));
            Instant endUtc = window.getEndTimeUtc() != null ? window.getEndTimeUtc()
                    : (window.getEndTime() == null ? null : toInstant(window.getEndTime()));
            if (startUtc != null && endUtc != null && bookingRepository.existsOverlappingBySlotIdAndStatusInUtc(
                    window.getId(),
                    SLOT_LOCKING_STATUSES,
                    startUtc,
                    endUtc
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
            BookingTransitionExecutor.apply(pendingBooking, BookingTransitionCommand.SYSTEM_REJECT, currentTime);
            pendingBooking.setRejectReason(reason);
        }
        bookingRepository.saveAll(pendingBookings);
        for (Booking pendingBooking : pendingBookings) {
            notificationCommandPort.publish(new NotificationCommandPort.NotificationIntent(
                    pendingBooking.getMentee().getId(), "BOOKING_AUTO_REJECTED",
                    "Yêu cầu đặt lịch không còn hiệu lực",
                    "Yêu cầu đặt lịch của bạn đã bị từ chối: " + reason,
                    "BOOKING", pendingBooking.getId(), "/bookings/" + pendingBooking.getId()));
        }
    }

    private void validateManagedActiveMentor(UUID mentorUserId) {
        requireUserId(mentorUserId);
        MentorBookingCapability capability = mentorBookingQueryPort.getBookingCapability(mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_CONFLICT, "Không tìm thấy thông tin mentor"));
        if (!capability.isActiveMentor()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ mentor đã được xác thực mới có thể cấu hình lịch rảnh");
        }
    }

    private List<ServiceSlotCandidate> resolveManagedServices(UUID mentorUserId, Collection<UUID> requestedServiceIds) {
        if (requestedServiceIds == null || requestedServiceIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, ServiceSlotCandidate> services = mentorBookingQueryPort.getServicesByIds(requestedServiceIds);

        Set<UUID> missingServiceIds = requestedServiceIds.stream()
                .filter(serviceId -> !services.containsKey(serviceId))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!missingServiceIds.isEmpty()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Một hoặc nhiều dịch vụ không tồn tại");
        }

        for (ServiceSlotCandidate service : services.values()) {
            if (service.mentorUserId() == null || !service.mentorUserId().equals(mentorUserId) || !Boolean.TRUE.equals(service.active())) {
                throw new BaseException(ErrorCode.ACCESS_DENIED, "Dịch vụ không hợp lệ hoặc không thuộc về bạn");
            }
        }
        return new ArrayList<>(services.values());
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
                .createdAt(BookingTime.toOffsetDateTime(rule.getCreatedAt()))
                .updatedAt(BookingTime.toOffsetDateTime(rule.getUpdatedAt()))
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

    private MentorAvailabilitySlot findSlotForUpdateOrLegacyRead(UUID slotId) {
        return mentorAvailabilitySlotRepository.findByIdForUpdate(slotId)
                .or(() -> mentorAvailabilitySlotRepository.findById(slotId))
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy slot rảnh"));
    }

    private Integer normalizedVersion(MentorAvailabilitySlot slot) {
        return slot.getVersion() == null ? 0 : slot.getVersion();
    }

    private void validateParentSlotDuration(LocalDateTime start, LocalDateTime end) {
        if (Duration.between(start, end).toMinutes() > MAXIMUM_PARENT_SLOT_DURATION_MINUTES) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "AVAILABILITY_RANGE_TOO_LARGE");
        }
    }

    private VersionConflictException slotVersionConflict(UUID slotId, Integer expectedVersion, Integer currentVersion) {
        return new VersionConflictException(
                ErrorCode.RESOURCE_CONFLICT,
                "AVAILABILITY_SLOT_VERSION_CONFLICT",
                slotId,
                expectedVersion,
                currentVersion
        );
    }

    private Instant normalizeToWholeMinute(Instant value) {
        if (value == null) {
            return null;
        }
        return value.truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
    }

    private LocalDateTime toUtcLocal(Instant value) {
        return BookingTime.fromInstant(value);
    }

    private Instant toInstant(LocalDateTime value) {
        return BookingTime.toInstant(value);
    }

    private boolean hasLockingBookings(UUID slotId) {
        return bookingRepository.countBySlotIdAndStatusIn(slotId, SLOT_LOCKING_STATUSES) > 0;
    }

    private void rejectPendingBookings(Collection<Booking> bookings, String reason) {
        LocalDateTime currentTime = now();
        for (Booking booking : bookings) {
            if (booking.getStatus() != BookingStatus.PENDING) {
                continue;
            }
            BookingTransitionExecutor.apply(booking, BookingTransitionCommand.SYSTEM_REJECT, currentTime);
            booking.setRejectReason(reason);
        }
        bookingRepository.saveAll(bookings);
    }

    private SlotMutationCapabilityResponse bindingRemovalCapability(MentorAvailabilitySlot slot, UUID serviceId) {
        if (hasLockingBookings(slot.getId())) {
            return new SlotMutationCapabilityResponse(SlotMutationMode.BLOCKED_BY_LOCKING_BOOKING, "SLOT_HAS_LOCKING_BOOKINGS", 0);
        }
        int affected = Math.toIntExact(bookingRepository.findBySlotIdAndStatus(slot.getId(), BookingStatus.PENDING).stream()
                .filter(booking -> booking.getServiceId() != null && serviceId.equals(booking.getServiceId()))
                .filter(booking -> selectedStartTime(booking) != null && selectedStartTime(booking).isAfter(now()))
                .count());
        return affected == 0
                ? new SlotMutationCapabilityResponse(SlotMutationMode.ALLOWED, null, 0)
                : new SlotMutationCapabilityResponse(SlotMutationMode.REQUIRES_PENDING_REJECTION, "SLOT_HAS_PENDING_BOOKINGS", affected);
    }

    private SlotMutationCapabilityResponse timeMutationCapability(MentorAvailabilitySlot slot) {
        if (hasLockingBookings(slot.getId())) {
            return new SlotMutationCapabilityResponse(SlotMutationMode.BLOCKED_BY_LOCKING_BOOKING, "SLOT_HAS_LOCKING_BOOKINGS", 0);
        }
        int pending = Math.toIntExact(bookingRepository.findBySlotIdAndStatus(slot.getId(), BookingStatus.PENDING).stream()
                .filter(booking -> selectedStartTime(booking) != null && selectedStartTime(booking).isAfter(now()))
                .count());
        return pending == 0
                ? new SlotMutationCapabilityResponse(SlotMutationMode.ALLOWED, null, 0)
                : new SlotMutationCapabilityResponse(SlotMutationMode.REQUIRES_PENDING_REJECTION, "SLOT_HAS_PENDING_BOOKINGS", pending);
    }

    private SlotMutationCapabilityResponse deactivationCapability(MentorAvailabilitySlot slot) {
        return timeMutationCapability(slot);
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
        return timeProvider.nowBusiness();
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
