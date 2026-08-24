package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.booking.domain.AvailabilitySlotService;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionCommand;
import com.fptu.exe.skillswap.modules.booking.repository.AvailabilitySlotServiceRepository;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.booking.service.AvailabilityTemplateService;
import com.fptu.exe.skillswap.modules.booking.service.BookingTransitionExecutor;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.port.GoogleCalendarConnectionPort;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceDeliveryMode;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorServiceManagementResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorServiceConstraintsResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.request.CreateMentorServiceRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.request.UpdateMentorServiceRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorServiceActiveRequest;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.payment.service.PricingPolicy;
import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import com.fptu.exe.skillswap.shared.exception.VersionConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MentorServiceManagementService {

    private final MentorServiceRepository mentorServiceRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final UserRepository userRepository;
    private final MentorProfileService mentorProfileService;
    private final GoogleCalendarConnectionPort googleCalendarConnectionPort;
    private final BookingRepository bookingRepository;
    private final AvailabilitySlotServiceRepository availabilitySlotServiceRepository;
    private final MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;
    private final PaymentProperties paymentProperties;
    private AvailabilityTemplateService availabilityTemplateService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setAvailabilityTemplateService(AvailabilityTemplateService availabilityTemplateService) {
        this.availabilityTemplateService = availabilityTemplateService;
    }

    @Transactional(readOnly = true)
    public List<MentorServiceManagementResponse> getMyServices(UUID mentorUserId, Boolean isActive) {
        MentorProfile mentorProfile = requireEligibleMentorProfile(mentorUserId);
        List<MentorService> services = isActive == null
                ? mentorServiceRepository.findByMentorProfileUserIdOrderByCreatedAtAsc(mentorProfile.getUserId())
                : mentorServiceRepository.findByMentorProfileUserIdAndIsActiveOrderByCreatedAtAsc(mentorProfile.getUserId(), isActive);
        return services
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MentorServiceConstraintsResponse getServiceConstraints() {
        return new MentorServiceConstraintsResponse(
                PricingPolicy.ALLOWED_DURATIONS,
                PricingPolicy.MIN_PRICE_SCOIN_PER_MINUTE,
                PricingPolicy.MAX_PRICE_SCOIN_PER_MINUTE
        );
    }

    @Transactional(readOnly = true)
    public MentorServiceManagementResponse getMyServiceDetail(UUID mentorUserId, UUID serviceId) {
        MentorProfile mentorProfile = requireEligibleMentorProfile(mentorUserId);
        return toResponse(loadOwnedService(mentorProfile.getUserId(), serviceId));
    }

    @Transactional
    public MentorServiceManagementResponse createService(UUID mentorUserId, CreateMentorServiceRequest request) {
        MentorProfile mentorProfile = requireEligibleMentorProfile(mentorUserId);
        requireRequest(request);

        int durationMinutes = validateDuration(request.durationMinutes());
        boolean isFree = Boolean.TRUE.equals(request.isFree());
        String title = cleanRequired(request.title(), "Tiêu đề dịch vụ");
        String description = cleanRequired(request.description(), "Mô tả dịch vụ");
        String expectedOutcome = cleanRequired(request.expectedOutcome(), "Kết quả kỳ vọng");
        Integer priceScoin = normalizePriceScoin(isFree, request.priceScoin(), durationMinutes);
        MentorServiceDeliveryMode deliveryMode = request.deliveryMode() == null
                ? MentorServiceDeliveryMode.ONE_TO_ONE
                : request.deliveryMode();
        googleCalendarConnectionPort.requireActiveConnectionForServiceCreation(mentorUserId);

        MentorService service = MentorService.builder()
                .mentorProfile(mentorProfile)
                .title(title)
                .description(description)
                .expectedOutcome(expectedOutcome)
                .durationMinutes(durationMinutes)
                .isFree(isFree)
                .priceScoin(priceScoin)
                .isActive(true)
                .maintainPostSessionChat(Boolean.TRUE.equals(request.maintainPostSessionChat()))
                .deliveryMode(deliveryMode)
                .build();

        touchMentorActivity(mentorProfile, DateTimeUtil.now());
        return toResponse(mentorServiceRepository.save(service));
    }

    @Transactional
    public MentorServiceManagementResponse updateService(UUID mentorUserId, UUID serviceId, UpdateMentorServiceRequest request) {
        MentorProfile mentorProfile = requireEligibleMentorProfile(mentorUserId);
        requireRequest(request);

        MentorService service = loadOwnedService(mentorProfile.getUserId(), serviceId);
        boolean isFree = Boolean.TRUE.equals(request.isFree());

        if (!java.util.Objects.equals(request.expectedVersion(), service.getVersion())) {
            throw versionConflict(serviceId, request.expectedVersion(), service.getVersion());
        }

        service.setTitle(cleanRequired(request.title(), "Tiêu đề dịch vụ"));
        service.setDescription(cleanRequired(request.description(), "Mô tả dịch vụ"));
        service.setExpectedOutcome(cleanRequired(request.expectedOutcome(), "Kết quả kỳ vọng"));
        service.setFree(isFree);
        service.setPriceScoin(normalizePriceScoin(isFree, request.priceScoin(), service.getDurationMinutes()));
        service.setMaintainPostSessionChat(Boolean.TRUE.equals(request.maintainPostSessionChat()));
        touchMentorActivity(mentorProfile, DateTimeUtil.now());

        return toResponse(mentorServiceRepository.save(service));
    }

    @Transactional
    public MentorServiceManagementResponse changeActiveStatus(UUID mentorUserId, UUID serviceId, MentorServiceActiveRequest request) {
        MentorProfile mentorProfile = requireEligibleMentorProfile(mentorUserId);
        if (request == null || request.isActive() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Trạng thái active không được để trống");
        }

        MentorService service = loadOwnedService(mentorProfile.getUserId(), serviceId);
        if (!java.util.Objects.equals(request.expectedVersion(), service.getVersion())) {
            throw versionConflict(serviceId, request.expectedVersion(), service.getVersion());
        }
        if (Boolean.TRUE.equals(request.isActive()) && Boolean.TRUE.equals(request.rejectPendingBookings())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "rejectPendingBookings chỉ hợp lệ khi deactivate service");
        }
        if (Boolean.TRUE.equals(request.isActive()) && !service.isActive()) {
            googleCalendarConnectionPort.requireActiveConnectionForServiceCreation(mentorUserId);
        }
        if (!Boolean.TRUE.equals(request.isActive())) {
            LocalDateTime now = DateTimeUtil.now();
            List<Booking> affectedPending = bookingRepository.findByServiceIdAndStatus(serviceId, BookingStatus.PENDING).stream()
                    .filter(booking -> booking.getSelectedStartTime() != null && booking.getSelectedStartTime().isAfter(now))
                    .toList();
            if (!affectedPending.isEmpty() && !Boolean.TRUE.equals(request.rejectPendingBookings())) {
                throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "SERVICE_HAS_PENDING_BOOKINGS");
            }
            rejectPendingBookings(affectedPending, "MENTOR_SERVICE_DEACTIVATED");

            // Retiring a service never changes confirmed booking snapshots, but it must stop
            // reactivation from silently exposing the service through old future slots.
            List<AvailabilitySlotService> futureBindings = availabilitySlotServiceRepository
                    .findFutureActiveBindingsByServiceIdForUpdate(serviceId, now);
            Set<UUID> changedSlotIds = futureBindings.stream()
                    .map(binding -> binding.getSlot().getId())
                    .collect(java.util.stream.Collectors.toSet());
            if (!futureBindings.isEmpty()) {
                availabilitySlotServiceRepository.deleteAll(futureBindings);
                mentorAvailabilitySlotRepository.bumpVersions(changedSlotIds, DateTimeUtil.instantNow());
            }
        }
        service.setActive(request.isActive());
        touchMentorActivity(mentorProfile, DateTimeUtil.now());
        MentorServiceManagementResponse response = toResponse(mentorServiceRepository.save(service));
        if (availabilityTemplateService != null) availabilityTemplateService.markMentorDue(mentorUserId);
        return response;
    }

    private MentorProfile requireEligibleMentorProfile(UUID mentorUserId) {
        requireUserId(mentorUserId);
        MentorProfile profile = mentorProfileRepository.findWithUserByUserId(mentorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ mentor"));

        if (profile.getStatus() != MentorStatus.ACTIVE || profile.getVerifiedAt() == null) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ mentor đã được xác thực mới được quản lý dịch vụ mentoring");
        }
        if (!mentorProfileService.hasCompletedMentorProfile(mentorUserId)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Cần hoàn thiện hồ sơ mentor trước khi quản lý dịch vụ mentoring");
        }
        return profile;
    }

    private MentorService loadOwnedService(UUID mentorUserId, UUID serviceId) {
        if (serviceId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã dịch vụ không được để trống");
        }
        return mentorServiceRepository.findByIdAndMentorProfileUserId(serviceId, mentorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dịch vụ mentoring"));
    }

    private MentorServiceManagementResponse toResponse(MentorService service) {
        int basePrice = service.isFree() ? 0 : defaultInteger(service.getPriceScoin());
        int publicPrice = PricingPolicy.menteePayableScoin(basePrice, paymentProperties);
        int payout = PricingPolicy.mentorNetScoin(basePrice, paymentProperties);

        return MentorServiceManagementResponse.builder()
                .serviceId(service.getId())
                .mentorUserId(service.getMentorProfile() == null ? null : service.getMentorProfile().getUserId())
                .title(service.getTitle())
                .description(service.getDescription())
                .expectedOutcome(service.getExpectedOutcome())
                .durationMinutes(service.getDurationMinutes())
                .isFree(service.isFree())
                .basePriceScoin(basePrice)
                .publicPriceScoin(publicPrice)
                .estimatedMentorPayoutScoin(payout)
                .isActive(service.isActive())
                .maintainPostSessionChat(service.isMaintainPostSessionChat())
                .deliveryMode(service.getDeliveryMode())
                .version(service.getVersion())
                .createdAt(service.getCreatedAt())
                .updatedAt(service.getUpdatedAt())
                .build();
    }

    private Integer validateDuration(Integer durationMinutes) {
        if (durationMinutes == null || !PricingPolicy.ALLOWED_DURATIONS.contains(durationMinutes)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thời lượng dịch vụ chỉ được chọn 30, 60, 90 hoặc 120 phút");
        }
        return durationMinutes;
    }

    private Integer normalizePriceScoin(Boolean isFree, Integer priceScoin, Integer durationMinutes) {
        if (Boolean.TRUE.equals(isFree)) {
            if (priceScoin != null && priceScoin > 0) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Dịch vụ miễn phí phải có priceScoin bằng 0");
            }
            return 0;
        }

        PricingPolicy.validatePaidServicePrice(priceScoin, durationMinutes);
        return priceScoin;
    }

    private int minimumPriceForDuration(Integer durationMinutes) {
        return PricingPolicy.minimumPriceForDuration(durationMinutes);
    }

    private int maximumPriceForDuration(Integer durationMinutes) {
        return PricingPolicy.maximumPriceForDuration(durationMinutes);
    }

    private String cleanRequired(String value, String label) {
        if (!hasText(value)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, label + " không được để trống");
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void requireRequest(Object request) {
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Dữ liệu dịch vụ mentoring không được để trống");
        }
    }

    private void requireUserId(UUID userId) {
        if (userId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (userRepository.findById(userId).isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy người dùng");
        }
    }

    private Integer defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private VersionConflictException versionConflict(UUID serviceId, Integer expectedVersion, Integer currentVersion) {
        return new VersionConflictException(
                ErrorCode.RESOURCE_CONFLICT,
                "MENTOR_SERVICE_VERSION_CONFLICT",
                serviceId,
                expectedVersion,
                currentVersion
        );
    }

    private void rejectPendingBookings(List<Booking> bookings, String reason) {
        LocalDateTime rejectedAt = DateTimeUtil.now();
        for (Booking booking : bookings) {
            if (booking.getStatus() != BookingStatus.PENDING) {
                continue;
            }
            BookingTransitionExecutor.apply(booking, BookingTransitionCommand.SYSTEM_REJECT, rejectedAt);
            booking.setRejectReason(reason);
        }
        bookingRepository.saveAll(bookings);
    }

    private void touchMentorActivity(MentorProfile profile, LocalDateTime activityTime) {
        if (profile == null || activityTime == null) {
            return;
        }
        if (profile.getLastActiveAt() == null || profile.getLastActiveAt().isBefore(activityTime)) {
            profile.setLastActiveAt(activityTime);
        }
    }

}
