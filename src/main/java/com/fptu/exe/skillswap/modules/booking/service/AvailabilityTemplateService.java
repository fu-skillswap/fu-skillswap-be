package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.infrastructure.config.AvailabilityTemplateProperties;
import com.fptu.exe.skillswap.modules.booking.domain.*;
import com.fptu.exe.skillswap.modules.booking.dto.request.*;
import com.fptu.exe.skillswap.modules.booking.dto.response.AvailabilitySlotServiceBasicResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.AvailabilityTemplateBlockedOccurrenceResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.AvailabilityTemplateResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.AvailabilityTemplateReplacementConflictResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.SlotMutationCapabilityResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.SlotMutationMode;
import com.fptu.exe.skillswap.modules.booking.event.AvailabilityTemplateReconciliationRequestedEvent;
import com.fptu.exe.skillswap.modules.booking.repository.*;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.mentor.service.MentorBookingPolicyService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.VersionConflictException;
import com.fptu.exe.skillswap.shared.exception.GeneratedOccurrenceReplacementException;
import com.fptu.exe.skillswap.shared.cursor.CursorCodec;
import com.fptu.exe.skillswap.shared.cursor.CursorTokenPayload;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AvailabilityTemplateService {

    private static final ZoneId TEMPLATE_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<BookingStatus> LOCKING_STATUSES = List.of(
            BookingStatus.ACCEPTED_AWAITING_PAYMENT, BookingStatus.ACCEPTED, BookingStatus.PAID);


    private final AvailabilityTemplateRepository templateRepository;
    private final AvailabilityTemplateExceptionRepository exceptionRepository;
    private final AvailabilityTemplateReconciliationRepository reconciliationRepository;
    private final AvailabilityMentorMutationLockRepository mutationLockRepository;
    private final MentorAvailabilitySlotRepository slotRepository;
    private final AvailabilitySlotServiceRepository slotServiceRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final MentorServiceRepository mentorServiceRepository;
    private final BookingRepository bookingRepository;

    private final MentorBookingPolicyService mentorBookingPolicyService;
    private final AvailabilityTemplateProperties properties;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;
    private final CursorCodec cursorCodec;

    @Autowired
    public AvailabilityTemplateService(AvailabilityTemplateRepository templateRepository,
                                       AvailabilityTemplateExceptionRepository exceptionRepository,
                                       AvailabilityTemplateReconciliationRepository reconciliationRepository,
                                       AvailabilityMentorMutationLockRepository mutationLockRepository,
                                       MentorAvailabilitySlotRepository slotRepository,
                                       AvailabilitySlotServiceRepository slotServiceRepository,
                                       MentorProfileRepository mentorProfileRepository,
                                       MentorServiceRepository mentorServiceRepository,
                                       BookingRepository bookingRepository,

                                       @Lazy MentorBookingPolicyService mentorBookingPolicyService,
                                       AvailabilityTemplateProperties properties,
                                       EntityManager entityManager,
                                       ApplicationEventPublisher eventPublisher,
                                       CursorCodec cursorCodec,
                                       MeterRegistry meterRegistry) {
        this(templateRepository, exceptionRepository, reconciliationRepository, mutationLockRepository, slotRepository,
                slotServiceRepository, mentorProfileRepository, mentorServiceRepository, bookingRepository,
                mentorBookingPolicyService, properties, entityManager, eventPublisher, cursorCodec);
        Gauge.builder("availability.template.reconciliation.backlog", reconciliationRepository,
                        repo -> repo.countDue(DateTimeUtil.now()))
                .description("Availability templates waiting for reconciliation").register(meterRegistry);
    }

    @Transactional
    public AvailabilityTemplateResponse create(UUID mentorUserId, CreateAvailabilityTemplateRequest request) {
        lockMentor(mentorUserId);
        MentorProfile mentor = requireMentor(mentorUserId);
        LocalDate today = today();
        validateCreate(request, today);
        validateActiveLimit(mentorUserId, null, today);
        AvailabilityTemplate template = AvailabilityTemplate.builder()
                .mentorProfile(mentor).startTime(request.startTime()).endTime(request.endTime())
                .weekdays(encodeDays(request.weekdays())).effectiveFrom(request.effectiveFrom())
                .effectiveTo(request.effectiveTo()).note(trimToNull(request.note()))
                .configuredStatus(AvailabilityTemplateConfiguredStatus.ACTIVE)
                .services(new LinkedHashSet<>(resolveActiveServices(mentorUserId, request.serviceIds())))
                .build();
        validateRecurrenceOverlap(template, null, today);
        templateRepository.saveAndFlush(template);
        reconciliationRepository.save(AvailabilityTemplateReconciliation.builder().template(template).templateId(template.getId())
                .nextReconcileAt(now()).build());
        reconcileLocked(template, false);
        return toResponse(template);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<AvailabilityTemplateResponse> list(UUID mentorUserId,
                                                                   AvailabilityTemplateConfiguredStatus configuredStatus,
                                                                   AvailabilityTemplateEffectiveStatus effectiveStatus,
                                                                   String cursor,
                                                                   Integer limit) {
        requireMentor(mentorUserId);
        int resolvedLimit = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
        String filterHash = hash("availability-templates|" + mentorUserId + "|" + configuredStatus + "|" + effectiveStatus);
        TemplateCursor decoded = decodeCursor(cursor, filterHash);
        List<AvailabilityTemplate> matching = templateRepository.findOwnedWithServices(mentorUserId).stream()
                .filter(template -> configuredStatus == null || template.getConfiguredStatus() == configuredStatus)
                .filter(template -> effectiveStatus == null || effectiveStatus(template) == effectiveStatus)
                .filter(template -> afterCursor(template, decoded))
                .limit(resolvedLimit + 1L)
                .toList();
        boolean hasNext = matching.size() > resolvedLimit;
        List<AvailabilityTemplate> items = hasNext ? matching.subList(0, resolvedLimit) : matching;
        String nextCursor = hasNext && !items.isEmpty()
                ? encodeCursor(items.get(items.size() - 1), filterHash) : null;
        return CursorPageResponse.<AvailabilityTemplateResponse>builder()
                .items(items.stream().map(this::toResponse).toList())
                .nextCursor(nextCursor).prevCursor(null).hasNext(hasNext).hasPrev(false).limit(resolvedLimit).build();
    }

    @Transactional(readOnly = true)
    public AvailabilityTemplateResponse get(UUID mentorUserId, UUID templateId) {
        requireMentor(mentorUserId);
        return toResponse(loadOwned(templateId, mentorUserId));
    }

    @Transactional
    public AvailabilityTemplateResponse update(UUID mentorUserId, UUID templateId, UpdateAvailabilityTemplateRequest request) {
        lockMentor(mentorUserId);
        AvailabilityTemplate template = loadOwnedForUpdate(templateId, mentorUserId);
        requireVersion(template, request.expectedVersion());
        LocalDate today = today();
        LocalDate effectiveFrom = template.getEffectiveFrom();
        if (request.effectiveFrom() != null && !request.effectiveFrom().equals(effectiveFrom)) {
            if (!effectiveFrom.isAfter(today)) throw conflict("AVAILABILITY_TEMPLATE_EFFECTIVE_FROM_IMMUTABLE");
            if (request.effectiveFrom().isBefore(today)) throw invalid("AVAILABILITY_TEMPLATE_INVALID_SCHEDULE");
            effectiveFrom = request.effectiveFrom();
        }
        validateSchedule(request.startTime(), request.endTime(), request.weekdays(), effectiveFrom, request.effectiveTo());
        template.setStartTime(request.startTime()); template.setEndTime(request.endTime());
        template.setWeekdays(encodeDays(request.weekdays())); template.setEffectiveFrom(effectiveFrom);
        template.setEffectiveTo(request.effectiveTo()); template.setNote(trimToNull(request.note()));
        template.setServices(new LinkedHashSet<>(resolveActiveServices(mentorUserId, request.serviceIds())));
        validateRecurrenceOverlap(template, template.getId(), today);
        bumpConfigVersion(template);
        reconcileLocked(template, Boolean.TRUE.equals(request.rejectPendingBookings()));
        markDue(template.getId());
        return toResponse(template);
    }

    @Transactional
    public AvailabilityTemplateResponse pause(UUID mentorUserId, UUID templateId, AvailabilityTemplateVersionRequest request) {
        lockMentor(mentorUserId);
        AvailabilityTemplate template = loadOwnedForUpdate(templateId, mentorUserId);
        requireVersion(template, request.expectedVersion());
        if (template.getConfiguredStatus() == AvailabilityTemplateConfiguredStatus.ARCHIVED) throw conflict("AVAILABILITY_TEMPLATE_ARCHIVED");
        template.setConfiguredStatus(AvailabilityTemplateConfiguredStatus.PAUSED);
        bumpConfigVersion(template);
        reconcileLocked(template, Boolean.TRUE.equals(request.rejectPendingBookings()));
        return toResponse(template);
    }

    @Transactional
    public AvailabilityTemplateResponse resume(UUID mentorUserId, UUID templateId, AvailabilityTemplateVersionRequest request) {
        lockMentor(mentorUserId);
        AvailabilityTemplate template = loadOwnedForUpdate(templateId, mentorUserId);
        requireVersion(template, request.expectedVersion());
        if (template.getConfiguredStatus() == AvailabilityTemplateConfiguredStatus.ARCHIVED) throw conflict("AVAILABILITY_TEMPLATE_ARCHIVED");
        if (effectiveStatus(template) == AvailabilityTemplateEffectiveStatus.EXPIRED) throw conflict("AVAILABILITY_TEMPLATE_EXPIRED");
        validateActiveLimit(mentorUserId, template.getId(), today());
        template.setConfiguredStatus(AvailabilityTemplateConfiguredStatus.ACTIVE);
        validateRecurrenceOverlap(template, template.getId(), today());
        bumpConfigVersion(template);
        reconcileLocked(template, Boolean.TRUE.equals(request.rejectPendingBookings()));
        return toResponse(template);
    }

    @Transactional
    public AvailabilityTemplateResponse archive(UUID mentorUserId, UUID templateId, AvailabilityTemplateVersionRequest request) {
        lockMentor(mentorUserId);
        AvailabilityTemplate template = loadOwnedForUpdate(templateId, mentorUserId);
        requireVersion(template, request.expectedVersion());
        template.setConfiguredStatus(AvailabilityTemplateConfiguredStatus.ARCHIVED);
        bumpConfigVersion(template);
        reconcileLocked(template, Boolean.TRUE.equals(request.rejectPendingBookings()));
        return toResponse(template);
    }

    @Transactional
    public AvailabilityTemplateResponse addException(UUID mentorUserId, UUID templateId, LocalDate date,
                                                     AvailabilityTemplateExceptionRequest request) {
        lockMentor(mentorUserId);
        AvailabilityTemplate template = loadOwnedForUpdate(templateId, mentorUserId);
        requireVersion(template, request.expectedVersion());
        validateOccurrence(template, date);
        if (exceptionRepository.findByTemplateIdAndOccurrenceDate(templateId, date).isPresent()) throw conflict("AVAILABILITY_TEMPLATE_EXCEPTION_EXISTS");
        MentorAvailabilitySlot slot = slotRepository.findByTemplateIdAndOccurrenceDate(templateId, date).orElse(null);
        deactivateForExplicitMutation(slot, Boolean.TRUE.equals(request.rejectPendingBookings()));
        exceptionRepository.save(AvailabilityTemplateException.builder().template(template).occurrenceDate(date)
                .provenance(AvailabilityTemplateExceptionProvenance.MENTOR).build());
        bumpConfigVersion(template);
        markDue(templateId);
        return toResponse(template);
    }

    @Transactional
    public AvailabilityTemplateResponse restoreException(UUID mentorUserId, UUID templateId, LocalDate date,
                                                          AvailabilityTemplateVersionRequest request) {
        lockMentor(mentorUserId);
        AvailabilityTemplate template = loadOwnedForUpdate(templateId, mentorUserId);
        requireVersion(template, request.expectedVersion());
        AvailabilityTemplateException exception = exceptionRepository.findByTemplateIdAndOccurrenceDate(templateId, date)
                .orElseThrow(() -> conflict("AVAILABILITY_TEMPLATE_EXCEPTION_NOT_FOUND"));
        exceptionRepository.delete(exception);
        bumpConfigVersion(template);
        reconcileLocked(template, Boolean.TRUE.equals(request.rejectPendingBookings()));
        return toResponse(template);
    }

    /** Called by direct-slot mutations before their own overlap check. */
    @Transactional
    public void replaceGeneratedOccurrences(UUID mentorUserId, LocalDateTime start, LocalDateTime end,
                                            boolean requested, boolean rejectPending,
                                            List<ExpectedTemplateVersionRequest> expectedVersions) {
        lockMentor(mentorUserId);
        List<MentorAvailabilitySlot> overlaps = slotRepository.findActiveGeneratedOverlaps(mentorUserId, start, end);
        if (overlaps.isEmpty()) return;
        if (!requested) {
            throw new GeneratedOccurrenceReplacementException(overlaps.stream()
                    .filter(slot -> slot.getTemplate() != null)
                    .map(slot -> new AvailabilityTemplateReplacementConflictResponse(slot.getTemplate().getId(),
                            slot.getTemplateOccurrenceDate(), slot.getTemplate().getConfigVersion()))
                    .distinct().toList());
        }
        Map<UUID, Integer> supplied = expectedVersions == null ? Map.of() : expectedVersions.stream()
                .collect(Collectors.toMap(ExpectedTemplateVersionRequest::templateId, ExpectedTemplateVersionRequest::expectedVersion, (a, b) -> a));
        List<AvailabilityTemplate> templates = overlaps.stream().map(MentorAvailabilitySlot::getTemplate)
                .filter(Objects::nonNull).collect(Collectors.collectingAndThen(Collectors.toMap(AvailabilityTemplate::getId, t -> t), map -> map.values().stream().sorted(Comparator.comparing(AvailabilityTemplate::getId)).toList()));
        for (AvailabilityTemplate template : templates) {
            AvailabilityTemplate locked = templateRepository.findOwnedForUpdate(template.getId(), mentorUserId)
                    .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Availability template không tồn tại"));
            if (!Objects.equals(supplied.get(locked.getId()), locked.getConfigVersion())) throw conflict("AVAILABILITY_TEMPLATE_VERSION_CONFLICT");
        }
        for (MentorAvailabilitySlot slot : overlaps) {
            deactivateForExplicitMutation(slot, rejectPending);
            AvailabilityTemplate template = slot.getTemplate();
            exceptionRepository.findByTemplateIdAndOccurrenceDate(template.getId(), slot.getTemplateOccurrenceDate())
                    .orElseGet(() -> exceptionRepository.save(AvailabilityTemplateException.builder().template(template)
                            .occurrenceDate(slot.getTemplateOccurrenceDate())
                            .provenance(AvailabilityTemplateExceptionProvenance.MANUAL_REPLACEMENT).build()));
            bumpConfigVersion(template);
        }
    }

    @Transactional
    public void deactivateGeneratedSlot(UUID mentorUserId, MentorAvailabilitySlot slot, Integer expectedTemplateVersion,
                                        boolean rejectPending) {
        lockMentor(mentorUserId);
        AvailabilityTemplate template = slot.getTemplate();
        if (template == null) throw invalid("GENERATED_SLOT_MANAGED_BY_TEMPLATE");
        AvailabilityTemplate locked = templateRepository.findOwnedForUpdate(template.getId(), mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Availability template không tồn tại"));
        requireVersion(locked, expectedTemplateVersion);
        deactivateForExplicitMutation(slot, rejectPending);
        exceptionRepository.findByTemplateIdAndOccurrenceDate(locked.getId(), slot.getTemplateOccurrenceDate())
                .orElseGet(() -> exceptionRepository.save(AvailabilityTemplateException.builder().template(locked)
                        .occurrenceDate(slot.getTemplateOccurrenceDate()).provenance(AvailabilityTemplateExceptionProvenance.MENTOR).build()));
        bumpConfigVersion(locked);
    }

    @Transactional(readOnly = true)
    public boolean isGeneratedSlotEligible(MentorAvailabilitySlot slot) {
        if (slot == null || slot.getTemplate() == null) return true;
        AvailabilityTemplate template = templateRepository.findWithServicesById(slot.getTemplate().getId()).orElse(null);
        if (template == null || effectiveStatus(template) != AvailabilityTemplateEffectiveStatus.ACTIVE) return false;
        LocalDate date = slot.getTemplateOccurrenceDate();
        if (date == null || !appliesOn(template, date)) return false;
        if (exceptionRepository.findByTemplateIdAndOccurrenceDate(template.getId(), date).isPresent()) return false;
        LocalDateTime expectedStart = toStored(date, template.getStartTime());
        LocalDateTime expectedEnd = toStored(date, template.getEndTime());
        return expectedStart.equals(slot.getStartTime()) && expectedEnd.equals(slot.getEndTime())
                && template.getServices().stream().anyMatch(MentorService::isActive);
    }

    @Transactional
    public void markSlotDue(UUID slotId) {
        if (slotId == null) return;
        slotRepository.findById(slotId).ifPresent(slot -> {
            if (slot.getTemplate() != null) {
                markDue(slot.getTemplate().getId());
                eventPublisher.publishEvent(new AvailabilityTemplateReconciliationRequestedEvent(slot.getTemplate().getId()));
            }
            else markMentorDue(slot.getMentorProfile().getUserId());
        });
    }

    @Transactional
    public void markMentorDue(UUID mentorUserId) {
        templateRepository.findOwnedWithServices(mentorUserId).forEach(template -> {
            markDue(template.getId());
            eventPublisher.publishEvent(new AvailabilityTemplateReconciliationRequestedEvent(template.getId()));
        });
    }

    @Transactional
    public void reconcileImmediately(UUID templateId) {
        AvailabilityTemplate template = templateRepository.findWithServicesById(templateId).orElse(null);
        if (template == null) return;
        lockMentor(template.getMentorProfile().getUserId());
        AvailabilityTemplate locked = templateRepository.findOwnedForUpdate(templateId, template.getMentorProfile().getUserId()).orElse(null);
        if (locked == null) return;
        try {
            reconcileLocked(locked, false);
            reconciliationRepository.findById(templateId).ifPresent(state -> {
                state.setLastReconciledAt(now());
                state.setLastErrorCode(null); state.setLastErrorMessage(null); state.setConsecutiveFailures(0);
                state.setNextReconcileAt(nextRollover());
            });
        } catch (RuntimeException exception) {
            markDue(templateId);
            throw exception;
        }
    }

    @Transactional
    public List<String> claimDueTemplates() {
        if (!properties.templatesEnabled()) return List.of();
        LocalDate today = today(); LocalDateTime current = now();
        List<UUID> ids = reconciliationRepository.findDueTemplateIdsForClaim(today, today.plusDays(properties.horizonDays() - 1), current,
                properties.schedulerBatchSize());
        UUID token = UUID.randomUUID();
        for (UUID id : ids) {
            AvailabilityTemplateReconciliation state = reconciliationRepository.findByTemplateIdForUpdate(id).orElse(null);
            if (state != null) {
                state.setClaimToken(token); state.setClaimedUntil(current.plusSeconds(properties.claimLeaseSeconds()));
                state.setLastAttemptAt(current);
            }
        }
        return ids.stream().map(id -> new ClaimedTemplate(id, token)).map(ClaimedTemplate::encode).toList();
    }

    @Transactional
    public boolean reconcileClaim(String claim) {
        ClaimedTemplate claimed = ClaimedTemplate.decode(claim);
        AvailabilityTemplate template = templateRepository.findWithServicesById(claimed.templateId()).orElse(null);
        if (template == null) return false;
        lockMentor(template.getMentorProfile().getUserId());
        AvailabilityTemplate locked = templateRepository.findOwnedForUpdate(template.getId(), template.getMentorProfile().getUserId()).orElse(null);
        AvailabilityTemplateReconciliation state = reconciliationRepository.findByTemplateIdForUpdate(template.getId()).orElse(null);
        if (locked == null || state == null || !claimed.token().equals(state.getClaimToken())) return false;
        try {
            reconcileLocked(locked, false);
            state.setLastReconciledAt(now()); state.setLastErrorCode(null); state.setLastErrorMessage(null);
            state.setConsecutiveFailures(0); state.setNextReconcileAt(nextRollover());
            state.setClaimToken(null); state.setClaimedUntil(null);
            return true;
        } catch (RuntimeException exception) {
            state.setConsecutiveFailures(state.getConsecutiveFailures() + 1);
            state.setLastErrorCode(exception instanceof BaseException base ? base.getErrorCode().getCode() : "AVAILABILITY_TEMPLATE_RECONCILIATION_FAILED");
            state.setLastErrorMessage(trimError(exception.getMessage()));
            state.setNextReconcileAt(now().plusMinutes(Math.min(60, 1 << Math.min(6, state.getConsecutiveFailures()))));
            state.setClaimToken(null); state.setClaimedUntil(null);
            log.warn("Availability template {} reconciliation failed", template.getId(), exception);
            return false;
        }
    }

    private void reconcileLocked(AvailabilityTemplate template, boolean rejectPending) {
        LocalDate from = today(); LocalDate to = from.plusDays(properties.horizonDays() - 1);
        Map<LocalDate, MentorAvailabilitySlot> slots = slotRepository.findTemplateOccurrences(template.getId(), from, to).stream()
                .collect(Collectors.toMap(MentorAvailabilitySlot::getTemplateOccurrenceDate, slot -> slot, (a, b) -> a));
        Set<LocalDate> exceptions = exceptionRepository.findByTemplateIdAndOccurrenceDateIn(template.getId(), dates(from, to)).stream()
                .map(AvailabilityTemplateException::getOccurrenceDate).collect(Collectors.toSet());
        for (LocalDate date : dates(from, to)) {
            MentorAvailabilitySlot existing = slots.get(date);
            boolean desired = effectiveStatus(template) == AvailabilityTemplateEffectiveStatus.ACTIVE && appliesOn(template, date)
                    && !exceptions.contains(date) && hasActiveService(template) && canMaterialize(template, date);
            if (existing != null && isProtected(existing)) continue;
            if (!desired) {
                deactivateForReconciliation(existing, rejectPending);
                continue;
            }
            LocalDateTime start = toStored(date, template.getStartTime());
            LocalDateTime end = toStored(date, template.getEndTime());
            if (existing != null && existing.isActive() && start.equals(existing.getStartTime()) && end.equals(existing.getEndTime())) continue;
            if (!slotRepository.findActiveManualOverlaps(template.getMentorProfile().getUserId(), start, end).isEmpty()) continue;
            if (existing == null && slotRepository.existsOverlappingActiveSlot(template.getMentorProfile().getUserId(), start, end)) continue;
            if (existing == null) existing = MentorAvailabilitySlot.builder().mentorProfile(template.getMentorProfile())
                    .template(template).templateOccurrenceDate(date).timezone("Asia/Ho_Chi_Minh").isBooked(false)
                    .recurrenceRule("TEMPLATE").note(template.getNote()).build();
            existing.setStartTime(start); existing.setEndTime(end); existing.setActive(true); existing.setNote(template.getNote());
            existing.setTemplate(template); existing.setTemplateOccurrenceDate(date);
            MentorAvailabilitySlot saved = slotRepository.saveAndFlush(existing);
            replaceBindings(saved, template.getServices().stream().filter(MentorService::isActive).toList());
        }
    }

    private void deactivateForReconciliation(MentorAvailabilitySlot slot, boolean rejectPending) {
        if (slot == null || !slot.isActive() || isHistorical(slot) || isProtected(slot)) return;
        List<Booking> pending = pendingBookings(slot);
        if (!pending.isEmpty() && !rejectPending) return;
        rejectPending(pending, "Availability template đã thay đổi");
        slot.setActive(false); slot.setBooked(false);
    }

    private void deactivateForExplicitMutation(MentorAvailabilitySlot slot, boolean rejectPending) {
        if (slot == null || !slot.isActive()) return;
        if (isProtected(slot)) throw conflict("AVAILABILITY_TEMPLATE_HAS_LOCKING_BOOKINGS");
        List<Booking> pending = pendingBookings(slot);
        if (!pending.isEmpty() && !rejectPending) throw conflict("AVAILABILITY_TEMPLATE_HAS_PENDING_BOOKINGS");
        rejectPending(pending, "Availability template đã bị rút lại");
        slot.setActive(false); slot.setBooked(false);
    }

    private boolean isProtected(MentorAvailabilitySlot slot) {
        if (slot == null) return false;
        if (bookingRepository.existsOverlappingBySlotIdAndStatusIn(slot.getId(), LOCKING_STATUSES, slot.getStartTime(), slot.getEndTime())) return true;
        return false;
    }

    private List<Booking> pendingBookings(MentorAvailabilitySlot slot) {
        return bookingRepository.findBySlotIdAndStatus(slot.getId(), BookingStatus.PENDING).stream()
                .filter(booking -> booking.getSelectedStartTime() != null && booking.getSelectedStartTime().isAfter(now())).toList();
    }

    private void rejectPending(List<Booking> bookings, String reason) {
        for (Booking booking : bookings) {
            booking.setStatus(BookingStatus.REJECTED); booking.setRejectedAt(now()); booking.setRejectReason(reason);
        }
        if (!bookings.isEmpty()) bookingRepository.saveAll(bookings);
    }

    private void replaceBindings(MentorAvailabilitySlot slot, List<MentorService> services) {
        slotServiceRepository.deleteBySlotId(slot.getId());
        slot.getSlotServices().clear();
        for (MentorService service : services) {
            AvailabilitySlotService binding = AvailabilitySlotService.builder()
                    .id(new AvailabilitySlotServiceId(slot.getId(), service.getId())).slot(slot).service(service).build();
            slot.getSlotServices().add(binding);
        }
        slotRepository.save(slot);
    }

    private void validateCreate(CreateAvailabilityTemplateRequest request, LocalDate today) {
        if (request.effectiveFrom().isBefore(today)) throw invalid("AVAILABILITY_TEMPLATE_INVALID_SCHEDULE");
        validateSchedule(request.startTime(), request.endTime(), request.weekdays(), request.effectiveFrom(), request.effectiveTo());
    }

    private void validateSchedule(LocalTime start, LocalTime end, List<DayOfWeek> weekdays, LocalDate from, LocalDate to) {
        if (start == null || end == null || !end.isAfter(start) || Duration.between(start, end).toHours() > 12
                || weekdays == null || weekdays.isEmpty() || new HashSet<>(weekdays).size() != weekdays.size()
                || from == null || (to != null && to.isBefore(from))) throw invalid("AVAILABILITY_TEMPLATE_INVALID_SCHEDULE");
    }

    private void validateRecurrenceOverlap(AvailabilityTemplate candidate, UUID selfId, LocalDate today) {
        if (candidate.getConfiguredStatus() != AvailabilityTemplateConfiguredStatus.ACTIVE || effectiveStatus(candidate) == AvailabilityTemplateEffectiveStatus.EXPIRED) return;
        for (AvailabilityTemplate other : templateRepository.findAllByMentorForOverlap(candidate.getMentorProfile().getUserId())) {
            if (Objects.equals(other.getId(), selfId) || other.getConfiguredStatus() != AvailabilityTemplateConfiguredStatus.ACTIVE
                    || effectiveStatus(other) == AvailabilityTemplateEffectiveStatus.EXPIRED) continue;
            if (recurrencesIntersect(candidate, other)) throw conflict("AVAILABILITY_TEMPLATE_OVERLAP");
        }
    }

    private boolean recurrencesIntersect(AvailabilityTemplate left, AvailabilityTemplate right) {
        if (!intervalsOverlap(left.getStartTime(), left.getEndTime(), right.getStartTime(), right.getEndTime())) return false;
        LocalDate from = left.getEffectiveFrom().isAfter(right.getEffectiveFrom()) ? left.getEffectiveFrom() : right.getEffectiveFrom();
        LocalDate to = minEnd(left.getEffectiveTo(), right.getEffectiveTo());
        if (to != null && to.isBefore(from)) return false;
        Set<DayOfWeek> common = decodeDays(left.getWeekdays()); common.retainAll(decodeDays(right.getWeekdays()));
        if (common.isEmpty()) return false;
        LocalDate cursor = from;
        for (int i = 0; i < 7 && (to == null || !cursor.isAfter(to)); i++, cursor = cursor.plusDays(1)) {
            if (common.contains(cursor.getDayOfWeek())) return true;
        }
        return false;
    }

    private void validateActiveLimit(UUID mentorUserId, UUID selfId, LocalDate today) {
        long active = templateRepository.findOwnedWithServices(mentorUserId).stream()
                .filter(template -> !Objects.equals(template.getId(), selfId))
                .filter(template -> template.getConfiguredStatus() == AvailabilityTemplateConfiguredStatus.ACTIVE)
                .filter(template -> template.getEffectiveTo() == null || !template.getEffectiveTo().isBefore(today)).count();
        if (active >= 20) throw conflict("AVAILABILITY_TEMPLATE_LIMIT_EXCEEDED");
    }

    private List<MentorService> resolveActiveServices(UUID mentorUserId, List<UUID> ids) {
        if (ids == null || ids.isEmpty() || new HashSet<>(ids).size() != ids.size()) throw invalid("AVAILABILITY_TEMPLATE_INACTIVE_SERVICE");
        List<MentorService> services = mentorServiceRepository.findAllById(ids);
        if (services.size() != ids.size() || services.stream().anyMatch(service -> !service.isActive()
                || service.getMentorProfile() == null || !mentorUserId.equals(service.getMentorProfile().getUserId()))) {
            throw invalid("AVAILABILITY_TEMPLATE_INACTIVE_SERVICE");
        }
        return services;
    }

    private void validateOccurrence(AvailabilityTemplate template, LocalDate date) {
        if (date == null || date.isBefore(today()) || !appliesOn(template, date)) throw invalid("AVAILABILITY_TEMPLATE_INVALID_OCCURRENCE");
    }

    private boolean appliesOn(AvailabilityTemplate template, LocalDate date) {
        return !date.isBefore(template.getEffectiveFrom()) && (template.getEffectiveTo() == null || !date.isAfter(template.getEffectiveTo()))
                && decodeDays(template.getWeekdays()).contains(date.getDayOfWeek());
    }

    private boolean canMaterialize(AvailabilityTemplate template, LocalDate date) {
        LocalDateTime start = toStored(date, template.getStartTime());
        return template.getServices().stream().filter(MentorService::isActive).anyMatch(service ->
                mentorBookingPolicyService.isBookableStartTime(template.getMentorProfile().getUserId(), start, now()));
    }

    private boolean hasActiveService(AvailabilityTemplate template) { return template.getServices().stream().anyMatch(MentorService::isActive); }
    private boolean isHistorical(MentorAvailabilitySlot slot) { return slot.getEndTime() == null || !slot.getEndTime().isAfter(now()); }

    private AvailabilityTemplateResponse toResponse(AvailabilityTemplate template) {
        LocalDate from = today(); LocalDate to = from.plusDays(properties.horizonDays() - 1);
        List<AvailabilityTemplateException> exceptions = exceptionRepository.findByTemplateIdAndOccurrenceDateIn(template.getId(), dates(from, to));
        List<AvailabilityTemplateBlockedOccurrenceResponse> blocked = new ArrayList<>();
        for (LocalDate date : dates(from, to)) if (appliesOn(template, date)) {
            LocalDateTime start = toStored(date, template.getStartTime()), end = toStored(date, template.getEndTime());
            slotRepository.findActiveManualOverlaps(template.getMentorProfile().getUserId(), start, end).stream().findFirst()
                    .ifPresent(slot -> blocked.add(new AvailabilityTemplateBlockedOccurrenceResponse(date, "MANUAL_SLOT_OVERLAP", slot.getId())));
        }
        return new AvailabilityTemplateResponse(template.getId(), template.getStartTime(), template.getEndTime(),
                decodeDays(template.getWeekdays()).stream().sorted().toList(), template.getEffectiveFrom(), template.getEffectiveTo(),
                template.getTimezone(), template.getNote(), template.getConfiguredStatus(), effectiveStatus(template),
                template.getConfigVersion(), template.getServices().stream().map(service -> new AvailabilitySlotServiceBasicResponse(
                        service.getId(), service.getTitle(), service.getDurationMinutes(), service.isFree(),
                        service.isFree() ? 0 : service.getPriceScoin(), new SlotMutationCapabilityResponse(SlotMutationMode.ALLOWED, null, 0))).toList(),
                hasActiveService(template) ? null : "NO_ACTIVE_SERVICE", exceptions.stream().map(AvailabilityTemplateException::getOccurrenceDate).sorted().toList(),
                blocked, template.getCreatedAt(), template.getUpdatedAt());
    }

    private AvailabilityTemplateEffectiveStatus effectiveStatus(AvailabilityTemplate template) {
        if (template.getConfiguredStatus() == AvailabilityTemplateConfiguredStatus.ARCHIVED) return AvailabilityTemplateEffectiveStatus.ARCHIVED;
        if (template.getEffectiveTo() != null && template.getEffectiveTo().isBefore(today())) return AvailabilityTemplateEffectiveStatus.EXPIRED;
        return template.getConfiguredStatus() == AvailabilityTemplateConfiguredStatus.PAUSED ? AvailabilityTemplateEffectiveStatus.PAUSED : AvailabilityTemplateEffectiveStatus.ACTIVE;
    }

    private AvailabilityTemplate loadOwned(UUID id, UUID mentorUserId) { return templateRepository.findWithServicesById(id)
            .filter(template -> template.getMentorProfile().getUserId().equals(mentorUserId))
            .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Availability template không tồn tại")); }
    private AvailabilityTemplate loadOwnedForUpdate(UUID id, UUID mentorUserId) { return templateRepository.findOwnedForUpdate(id, mentorUserId)
            .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Availability template không tồn tại")); }
    private MentorProfile requireMentor(UUID mentorUserId) { return mentorProfileRepository.findWithUserByUserId(mentorUserId)
            .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ mentor")); }
    private void lockMentor(UUID mentorUserId) {
        mutationLockRepository.findByMentorUserIdForUpdate(mentorUserId).orElseGet(() -> {
            mutationLockRepository.saveAndFlush(new AvailabilityMentorMutationLock(mentorUserId));
            return mutationLockRepository.findByMentorUserIdForUpdate(mentorUserId).orElseThrow();
        });
    }
    private void markDue(UUID templateId) { reconciliationRepository.findById(templateId).ifPresent(state -> state.setNextReconcileAt(now())); }
    private LocalDate today() { return LocalDate.now(TEMPLATE_ZONE); }
    private LocalDateTime now() { return DateTimeUtil.now(); }
    private LocalDateTime toStored(LocalDate date, LocalTime time) { return LocalDateTime.of(date, time).atZone(TEMPLATE_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(); }
    private LocalDateTime nextRollover() { LocalDateTime local = LocalDateTime.now(TEMPLATE_ZONE).plusDays(1).toLocalDate().atStartOfDay().plusMinutes(1); return local.atZone(TEMPLATE_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(); }
    private List<LocalDate> dates(LocalDate from, LocalDate to) { List<LocalDate> dates = new ArrayList<>(); for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) dates.add(date); return dates; }
    private String encodeDays(List<DayOfWeek> days) { return days.stream().distinct().sorted().map(Enum::name).collect(Collectors.joining(",")); }
    private Set<DayOfWeek> decodeDays(String encoded) { return Arrays.stream(encoded.split(",")).map(DayOfWeek::valueOf).collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class))); }
    private boolean intervalsOverlap(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) { return aStart.isBefore(bEnd) && aEnd.isAfter(bStart); }
    private LocalDate minEnd(LocalDate a, LocalDate b) { if (a == null) return b; if (b == null) return a; return a.isBefore(b) ? a : b; }
    private void requireVersion(AvailabilityTemplate template, Integer expected) { if (!Objects.equals(template.getConfigVersion(), expected)) throw new VersionConflictException(ErrorCode.AVAILABILITY_TEMPLATE_VERSION_CONFLICT, "Availability template đã được cập nhật", template.getId(), expected, template.getConfigVersion()); }
    private void bumpConfigVersion(AvailabilityTemplate template) { entityManager.lock(template, LockModeType.OPTIMISTIC_FORCE_INCREMENT); entityManager.flush(); }
    private BaseException conflict(String code) { return new BaseException(errorCode(code), code); }
    private BaseException invalid(String code) { return new BaseException(errorCode(code), code); }
    private ErrorCode errorCode(String code) {
        return switch (code) {
            case "AVAILABILITY_TEMPLATE_VERSION_CONFLICT" -> ErrorCode.AVAILABILITY_TEMPLATE_VERSION_CONFLICT;
            case "AVAILABILITY_TEMPLATE_OVERLAP" -> ErrorCode.AVAILABILITY_TEMPLATE_OVERLAP;
            case "AVAILABILITY_TEMPLATE_LIMIT_EXCEEDED" -> ErrorCode.AVAILABILITY_TEMPLATE_LIMIT_EXCEEDED;
            case "AVAILABILITY_TEMPLATE_INACTIVE_SERVICE" -> ErrorCode.AVAILABILITY_TEMPLATE_INACTIVE_SERVICE;
            case "AVAILABILITY_TEMPLATE_EXPIRED" -> ErrorCode.AVAILABILITY_TEMPLATE_EXPIRED;
            case "AVAILABILITY_TEMPLATE_HAS_PENDING_BOOKINGS" -> ErrorCode.AVAILABILITY_TEMPLATE_HAS_PENDING_BOOKINGS;
            case "AVAILABILITY_TEMPLATE_HAS_LOCKING_BOOKINGS" -> ErrorCode.AVAILABILITY_TEMPLATE_HAS_LOCKING_BOOKINGS;
            case "AVAILABILITY_TEMPLATE_EXCEPTION_EXISTS" -> ErrorCode.AVAILABILITY_TEMPLATE_EXCEPTION_EXISTS;
            case "AVAILABILITY_TEMPLATE_EXCEPTION_NOT_FOUND" -> ErrorCode.AVAILABILITY_TEMPLATE_EXCEPTION_NOT_FOUND;
            case "GENERATED_SLOT_MANAGED_BY_TEMPLATE" -> ErrorCode.GENERATED_SLOT_MANAGED_BY_TEMPLATE;
            case "GENERATED_OCCURRENCE_REPLACEMENT_REQUIRED" -> ErrorCode.GENERATED_OCCURRENCE_REPLACEMENT_REQUIRED;
            case "AVAILABILITY_TEMPLATE_INVALID_OCCURRENCE" -> ErrorCode.AVAILABILITY_TEMPLATE_INVALID_OCCURRENCE;
            case "AVAILABILITY_TEMPLATE_OCCURRENCE_UNAVAILABLE" -> ErrorCode.AVAILABILITY_TEMPLATE_OCCURRENCE_UNAVAILABLE;
            case "AVAILABILITY_TEMPLATE_INVALID_SCHEDULE", "AVAILABILITY_TEMPLATE_EFFECTIVE_FROM_IMMUTABLE" -> ErrorCode.AVAILABILITY_TEMPLATE_INVALID_SCHEDULE;
            default -> ErrorCode.RESOURCE_CONFLICT;
        };
    }
    private TemplateCursor decodeCursor(String cursor, String filterHash) {
        if (cursor == null || cursor.isBlank()) return new TemplateCursor(null, null);
        CursorTokenPayload payload = cursorCodec.decode(cursor);
        if (!filterHash.equals(payload.filterHash())) throw new BaseException(ErrorCode.BAD_REQUEST, "Cursor availability template không khớp bộ lọc");
        try {
            return new TemplateCursor(LocalDate.parse(payload.sortKey()), UUID.fromString(payload.secondaryKey()));
        } catch (RuntimeException exception) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cursor availability template không hợp lệ");
        }
    }
    private boolean afterCursor(AvailabilityTemplate template, TemplateCursor cursor) {
        if (cursor.effectiveFrom() == null) return true;
        int dateComparison = template.getEffectiveFrom().compareTo(cursor.effectiveFrom());
        return dateComparison < 0 || (dateComparison == 0 && template.getId().compareTo(cursor.id()) < 0);
    }
    private String encodeCursor(AvailabilityTemplate template, String filterHash) {
        return cursorCodec.encode(CursorTokenPayload.builder().sortKey(template.getEffectiveFrom().toString())
                .secondaryKey(template.getId().toString()).direction("NEXT").filterHash(filterHash)
                .issuedAt(Instant.now()).build());
    }
    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Không thể tạo cursor availability template", exception);
        }
    }
    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String trimError(String value) { return value == null ? null : value.length() <= 500 ? value : value.substring(0, 500); }

    private record ClaimedTemplate(UUID templateId, UUID token) {
        String encode() { return templateId + ":" + token; }
        static ClaimedTemplate decode(String value) { String[] split = value.split(":"); return new ClaimedTemplate(UUID.fromString(split[0]), UUID.fromString(split[1])); }
    }
    private record TemplateCursor(LocalDate effectiveFrom, UUID id) {}
}
