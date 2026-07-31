package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.admin.service.AdminAuditWriterService;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.GroupSession;
import com.fptu.exe.skillswap.modules.booking.domain.GroupSessionRegistrationStatus;
import com.fptu.exe.skillswap.modules.booking.domain.GroupSessionStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateGroupSessionRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.GroupSessionVersionRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.UpdateGroupSessionRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.UpdateGroupSessionCapacityRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.GroupSessionResponse;
import com.fptu.exe.skillswap.modules.booking.repository.AvailabilitySlotServiceRepository;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.GroupSessionRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceDeliveryMode;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.mentor.service.MentorBookingPolicyService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import com.fptu.exe.skillswap.shared.exception.VersionConflictException;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GroupSessionManagementService {

    private static final int MIN_CAPACITY = 2;
    private static final int MAX_CAPACITY = 20;
    private static final List<BookingStatus> LOCKING_BOOKING_STATUSES = List.of(
            BookingStatus.ACCEPTED_AWAITING_PAYMENT, BookingStatus.ACCEPTED, BookingStatus.PAID
    );
    private static final EnumSet<GroupSessionStatus> RESERVING_STATUSES = EnumSet.of(
            GroupSessionStatus.OPEN, GroupSessionStatus.IN_PROGRESS
    );

    private final MentorProfileRepository mentorProfileRepository;
    private final MentorServiceRepository mentorServiceRepository;
    private final MentorAvailabilitySlotRepository slotRepository;
    private final AvailabilitySlotServiceRepository slotServiceRepository;
    private final BookingRepository bookingRepository;
    private final GroupSessionRepository groupSessionRepository;
    private final MentorBookingPolicyService mentorBookingPolicyService;
    private final AdminAuditWriterService auditWriter;
    private final GroupSessionCommerceService groupSessionCommerceService;
    private GroupSessionExperienceService groupSessionExperienceService;

    @Autowired(required = false)
    void setGroupSessionExperienceService(GroupSessionExperienceService groupSessionExperienceService) {
        this.groupSessionExperienceService = groupSessionExperienceService;
    }

    @Transactional(readOnly = true)
    public List<GroupSessionResponse> listOwned(UUID mentorUserId, UUID serviceId) {
        requireEligibleMentor(mentorUserId);
        return groupSessionRepository.findByServiceIdAndMentorProfileUserIdOrderByScheduledStartAtAsc(serviceId, mentorUserId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public GroupSessionResponse getOwned(UUID mentorUserId, UUID groupSessionId) {
        requireEligibleMentor(mentorUserId);
        return toResponse(loadOwned(groupSessionId, mentorUserId));
    }

    @Transactional
    public GroupSessionResponse create(UUID mentorUserId, UUID serviceId, CreateGroupSessionRequest request) {
        MentorProfile mentor = lockEligibleMentor(mentorUserId);
        MentorService service = loadOwnedGroupServiceForUpdate(serviceId, mentorUserId);
        MentorAvailabilitySlot slot = lockBoundSlot(request.sourceSlotId(), service, mentorUserId);
        GroupSession session = buildDraft(mentor, service, slot, request);
        validateSchedule(session, mentorUserId, false);
        GroupSession saved = groupSessionRepository.save(session);
        audit(mentorUserId, saved, "GROUP_SESSION_CREATED", null, auditState(saved));
        return toResponse(saved);
    }

    @Transactional
    public GroupSessionResponse update(UUID mentorUserId, UUID groupSessionId, UpdateGroupSessionRequest request) {
        lockEligibleMentor(mentorUserId);
        GroupSession session = lockOwnedAfterCalendarLocks(groupSessionId, mentorUserId);
        requireVersion(session, request.expectedVersion());
        if (session.getStatus() != GroupSessionStatus.DRAFT) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "GROUP_SESSION_DRAFT_ONLY");
        }
        Map<String, Object> oldState = auditState(session);
        applyDraftMutation(session, request);
        validateSchedule(session, mentorUserId, true);
        GroupSession saved = groupSessionRepository.save(session);
        audit(mentorUserId, saved, "GROUP_SESSION_UPDATED", oldState, auditState(saved));
        return toResponse(saved);
    }

    @Transactional
    public GroupSessionResponse publish(UUID mentorUserId, UUID groupSessionId, GroupSessionVersionRequest request) {
        lockEligibleMentor(mentorUserId);
        GroupSession session = lockOwnedAfterCalendarLocks(groupSessionId, mentorUserId);
        requireVersion(session, request.expectedVersion());
        if (session.getStatus() != GroupSessionStatus.DRAFT) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "GROUP_SESSION_NOT_DRAFT");
        }
        validateSchedule(session, mentorUserId, true);
        Map<String, Object> oldState = auditState(session);
        copyServiceSnapshot(session, session.getService());
        session.setStatus(GroupSessionStatus.OPEN);
        session.setPublishedAt(now());
        GroupSession saved = groupSessionRepository.save(session);
        if (groupSessionExperienceService != null) {
            groupSessionExperienceService.createSharedExperience(saved);
        }
        audit(mentorUserId, saved, "GROUP_SESSION_PUBLISHED", oldState, auditState(saved));
        return toResponse(saved);
    }

    @Transactional
    public GroupSessionResponse closeRegistration(UUID mentorUserId, UUID groupSessionId, GroupSessionVersionRequest request) {
        lockEligibleMentor(mentorUserId);
        GroupSession session = loadOwnedForUpdate(groupSessionId, mentorUserId);
        requireVersion(session, request.expectedVersion());
        if (session.getStatus() != GroupSessionStatus.OPEN) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "GROUP_SESSION_REGISTRATION_NOT_OPEN");
        }
        Map<String, Object> oldState = auditState(session);
        session.setRegistrationStatus(GroupSessionRegistrationStatus.CLOSED);
        GroupSession saved = groupSessionRepository.save(session);
        audit(mentorUserId, saved, "GROUP_SESSION_REGISTRATION_CLOSED", oldState, auditState(saved));
        return toResponse(saved);
    }

    @Transactional
    public GroupSessionResponse increaseCapacity(UUID mentorUserId, UUID groupSessionId, UpdateGroupSessionCapacityRequest request) {
        lockEligibleMentor(mentorUserId);
        GroupSession session = loadOwnedForUpdate(groupSessionId, mentorUserId);
        requireVersion(session, request.expectedVersion());
        if (session.getStatus() != GroupSessionStatus.OPEN
                || session.getRegistrationStatus() != GroupSessionRegistrationStatus.OPEN) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "GROUP_SESSION_CAPACITY_UPDATE_NOT_ALLOWED");
        }
        int newCapacity = requireCapacity(request.maxParticipants());
        if (newCapacity <= session.getMaxParticipants() || newCapacity < session.getReservedSeatCount()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "GROUP_SESSION_CAPACITY_CAN_ONLY_INCREASE");
        }
        Map<String, Object> oldState = auditState(session);
        session.setMaxParticipants(newCapacity);
        GroupSession saved = groupSessionRepository.save(session);
        audit(mentorUserId, saved, "GROUP_SESSION_CAPACITY_INCREASED", oldState, auditState(saved));
        return toResponse(saved);
    }

    @Transactional
    public GroupSessionResponse cancel(UUID mentorUserId, UUID groupSessionId, GroupSessionVersionRequest request) {
        lockEligibleMentor(mentorUserId);
        GroupSession session = loadOwnedForUpdate(groupSessionId, mentorUserId);
        requireVersion(session, request.expectedVersion());
        if (session.getStatus() != GroupSessionStatus.DRAFT && session.getStatus() != GroupSessionStatus.OPEN) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "GROUP_SESSION_CANCELLATION_NOT_ALLOWED");
        }
        Map<String, Object> oldState = auditState(session);
        session.setStatus(GroupSessionStatus.CANCELLED);
        session.setRegistrationStatus(GroupSessionRegistrationStatus.CLOSED);
        session.setCancelledAt(now());
        groupSessionCommerceService.cancelSeatsForSession(session, "GROUP_SESSION_CANCELLED_BY_MENTOR");
        if (groupSessionExperienceService != null) {
            groupSessionExperienceService.revokeAllForCancelledSession(session);
        }
        GroupSession saved = groupSessionRepository.save(session);
        audit(mentorUserId, saved, "GROUP_SESSION_CANCELLED", oldState, auditState(saved));
        return toResponse(saved);
    }

    private GroupSession buildDraft(MentorProfile mentor, MentorService service, MentorAvailabilitySlot slot,
                                    CreateGroupSessionRequest request) {
        LocalDateTime start = utc(requireMinute(request.startAt()));
        LocalDateTime end = start.plusMinutes(service.getDurationMinutes());
        LocalDateTime close = request.registrationClosesAt() == null
                ? start.minusHours(1)
                : utc(requireMinute(request.registrationClosesAt()));
        return GroupSession.builder()
                .mentorProfile(mentor).service(service).sourceSlot(slot)
                .scheduledStartAt(start).scheduledEndAt(end)
                .maxParticipants(requireCapacity(request.maxParticipants()))
                .reservedSeatCount(0).status(GroupSessionStatus.DRAFT)
                .registrationStatus(GroupSessionRegistrationStatus.OPEN)
                .registrationClosesAt(close).sessionNote(trimToNull(request.sessionNote()))
                .serviceTitleSnapshot(service.getTitle())
                .serviceDescriptionSnapshot(service.getDescription())
                .serviceExpectedOutcomeSnapshot(service.getExpectedOutcome())
                .serviceDurationSnapshot(service.getDurationMinutes())
                .serviceIsFreeSnapshot(service.isFree())
                .servicePriceScoinSnapshot(Boolean.TRUE.equals(service.isFree()) ? 0 : service.getPriceScoin())
                .build();
    }

    private void applyDraftMutation(GroupSession session, UpdateGroupSessionRequest request) {
        LocalDateTime start = utc(requireMinute(request.startAt()));
        session.setScheduledStartAt(start);
        session.setScheduledEndAt(start.plusMinutes(session.getService().getDurationMinutes()));
        session.setMaxParticipants(requireCapacity(request.maxParticipants()));
        session.setRegistrationClosesAt(request.registrationClosesAt() == null
                ? start.minusHours(1) : utc(requireMinute(request.registrationClosesAt())));
        session.setSessionNote(trimToNull(request.sessionNote()));
        copyServiceSnapshot(session, session.getService());
    }

    /** Locks existing group and direct booking intervals before publishing the reservation. */
    private void validateSchedule(GroupSession session, UUID mentorUserId, boolean excludeSelf) {
        LocalDateTime now = now();
        if (!session.getScheduledStartAt().isAfter(now)
                || !session.getRegistrationClosesAt().isAfter(now)
                || !session.getRegistrationClosesAt().isBefore(session.getScheduledStartAt())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "GROUP_SESSION_REGISTRATION_CLOSE_INVALID");
        }
        MentorAvailabilitySlot slot = session.getSourceSlot();
        if (session.getScheduledStartAt().isBefore(slot.getStartTime()) || session.getScheduledEndAt().isAfter(slot.getEndTime())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "GROUP_SESSION_OUTSIDE_SOURCE_SLOT");
        }
        long offset = Duration.between(slot.getStartTime(), session.getScheduledStartAt()).toMinutes();
        if (offset < 0 || offset % session.getService().getDurationMinutes() != 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "GROUP_SESSION_CANDIDATE_ALIGNMENT_INVALID");
        }
        mentorBookingPolicyService.validateBookingWindow(mentorUserId, session.getScheduledStartAt(), now);
        List<GroupSession> overlaps = groupSessionRepository.findActiveOverlapsForUpdate(
                mentorUserId, RESERVING_STATUSES, session.getScheduledStartAt(), session.getScheduledEndAt());
        if (excludeSelf) {
            overlaps = overlaps.stream().filter(other -> !other.getId().equals(session.getId())).toList();
        }
        if (!overlaps.isEmpty()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "GROUP_SESSION_MENTOR_TIME_CONFLICT");
        }
        for (BookingStatus status : LOCKING_BOOKING_STATUSES) {
            List<Booking> bookings = bookingRepository.findOverlappingBySlotIdAndStatusForUpdate(
                    slot.getId(), status, session.getScheduledStartAt(), session.getScheduledEndAt());
            if (!bookings.isEmpty()) {
                throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "GROUP_SESSION_BOOKING_TIME_CONFLICT");
            }
        }
    }

    private MentorAvailabilitySlot lockBoundSlot(UUID slotId, MentorService service, UUID mentorUserId) {
        MentorAvailabilitySlot slot = slotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy availability slot"));
        if (!slot.isActive() || slot.getMentorProfile() == null || !mentorUserId.equals(slot.getMentorProfile().getUserId())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "GROUP_SESSION_SOURCE_SLOT_INVALID");
        }
        if (!slotServiceRepository.existsBySlotIdAndServiceId(slotId, service.getId())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "GROUP_SESSION_SERVICE_NOT_BOUND_TO_SLOT");
        }
        return slot;
    }

    /**
     * Preserve the calendar lock order for draft mutations and publish:
     * service -> source slot -> session -> overlapping reservations.
     */
    private GroupSession lockOwnedAfterCalendarLocks(UUID groupSessionId, UUID mentorUserId) {
        GroupSession snapshot = loadOwned(groupSessionId, mentorUserId);
        MentorService service = loadOwnedGroupServiceForUpdate(snapshot.getService().getId(), mentorUserId);
        MentorAvailabilitySlot slot = lockBoundSlot(snapshot.getSourceSlot().getId(), service, mentorUserId);
        GroupSession session = loadOwnedForUpdate(groupSessionId, mentorUserId);
        session.setService(service);
        session.setSourceSlot(slot);
        return session;
    }

    private MentorService loadOwnedGroupServiceForUpdate(UUID serviceId, UUID mentorUserId) {
        MentorService service = mentorServiceRepository.findByIdAndMentorProfileUserIdForUpdate(serviceId, mentorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dịch vụ mentoring"));
        if (!service.isActive() || service.getDeliveryMode() != MentorServiceDeliveryMode.GROUP_SESSION) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "GROUP_SESSION_SERVICE_REQUIRED");
        }
        return service;
    }

    private MentorProfile lockEligibleMentor(UUID mentorUserId) {
        MentorProfile mentor = mentorProfileRepository.findWithUserByUserIdForUpdate(mentorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ mentor"));
        if (mentor.getStatus() != MentorStatus.ACTIVE || mentor.getVerifiedAt() == null) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "GROUP_SESSION_MENTOR_INELIGIBLE");
        }
        return mentor;
    }

    private MentorProfile requireEligibleMentor(UUID mentorUserId) {
        MentorProfile mentor = mentorProfileRepository.findWithUserByUserId(mentorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ mentor"));
        if (mentor.getStatus() != MentorStatus.ACTIVE || mentor.getVerifiedAt() == null) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "GROUP_SESSION_MENTOR_INELIGIBLE");
        }
        return mentor;
    }

    private GroupSession loadOwned(UUID groupSessionId, UUID mentorUserId) {
        return groupSessionRepository.findByIdAndMentorProfileUserId(groupSessionId, mentorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy group session"));
    }

    private GroupSession loadOwnedForUpdate(UUID groupSessionId, UUID mentorUserId) {
        return groupSessionRepository.findOwnedByIdForUpdate(groupSessionId, mentorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy group session"));
    }

    private void requireVersion(GroupSession session, Integer expectedVersion) {
        if (!java.util.Objects.equals(session.getVersion(), expectedVersion)) {
            throw new VersionConflictException(ErrorCode.RESOURCE_CONFLICT, "GROUP_SESSION_VERSION_CONFLICT",
                    session.getId(), expectedVersion, session.getVersion());
        }
    }

    private int requireCapacity(Integer capacity) {
        if (capacity == null || capacity < MIN_CAPACITY || capacity > MAX_CAPACITY) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "GROUP_SESSION_CAPACITY_INVALID");
        }
        return capacity;
    }

    private Instant requireMinute(Instant value) {
        if (value == null || value.getNano() != 0 || value.getEpochSecond() % 60 != 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "SCHEDULING_TIME_PRECISION_INVALID");
        }
        return value;
    }

    private void copyServiceSnapshot(GroupSession session, MentorService service) {
        session.setServiceTitleSnapshot(service.getTitle());
        session.setServiceDescriptionSnapshot(service.getDescription());
        session.setServiceExpectedOutcomeSnapshot(service.getExpectedOutcome());
        session.setServiceDurationSnapshot(service.getDurationMinutes());
        session.setServiceIsFreeSnapshot(service.isFree());
        session.setServicePriceScoinSnapshot(Boolean.TRUE.equals(service.isFree()) ? 0 : service.getPriceScoin());
    }

    private LocalDateTime utc(Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
    private Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(DateTimeUtil.getClock().instant(), ZoneOffset.UTC); }
    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private GroupSessionResponse toResponse(GroupSession session) {
        return new GroupSessionResponse(session.getId(), session.getService().getId(), session.getSourceSlot().getId(),
                instant(session.getScheduledStartAt()), instant(session.getScheduledEndAt()), session.getMaxParticipants(),
                session.getReservedSeatCount(), session.getStatus(), session.getRegistrationStatus(),
                instant(session.getRegistrationClosesAt()), session.getSessionNote(), session.getServiceTitleSnapshot(),
                session.getServiceDescriptionSnapshot(), session.getServiceExpectedOutcomeSnapshot(),
                session.getServiceDurationSnapshot(), session.getServiceIsFreeSnapshot(), session.getServicePriceScoinSnapshot(), session.getVersion(),
                instant(session.getCreatedAt()), instant(session.getUpdatedAt()), instant(session.getPublishedAt()), instant(session.getCancelledAt()));
    }

    private Map<String, Object> auditState(GroupSession session) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("status", session.getStatus());
        values.put("registrationStatus", session.getRegistrationStatus());
        values.put("startAt", session.getScheduledStartAt());
        values.put("endAt", session.getScheduledEndAt());
        values.put("maxParticipants", session.getMaxParticipants());
        return values;
    }

    private void audit(UUID actor, GroupSession session, String type, Map<String, Object> oldState, Map<String, Object> newState) {
        auditWriter.writeOperatorEvent(actor, "GROUP_SESSION", session.getId(), type, oldState, newState);
    }
}
