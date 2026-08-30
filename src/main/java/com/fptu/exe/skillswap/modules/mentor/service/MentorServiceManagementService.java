package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.booking.port.BookingAvailabilityPort;
import com.fptu.exe.skillswap.modules.identity.port.GoogleCalendarConnectionPort;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceDeliveryMode;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.dto.request.CreateMentorServiceRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorServiceActiveRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.request.UpdateMentorServiceRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorServiceConstraintsResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorServiceManagementResponse;
import com.fptu.exe.skillswap.modules.mentor.event.MentorBookingPolicyUpdatedEvent;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import com.fptu.exe.skillswap.shared.exception.VersionConflictException;
import com.fptu.exe.skillswap.shared.policy.PricingPolicy;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final UserQueryPort userQueryPort;
    private final MentorProfileService mentorProfileService;
    private final GoogleCalendarConnectionPort googleCalendarConnectionPort;
    private final BookingAvailabilityPort bookingAvailabilityPort;
    private final PaymentProperties paymentProperties;
    private final ApplicationEventPublisher applicationEventPublisher;

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
                PricingPolicy.allowedServiceDurations(paymentProperties),
                paymentProperties.getMinPriceScoinPerMinute(),
                paymentProperties.getMaxPriceScoinPerMinute()
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

        MentorService service = MentorService.builder()
                .mentorProfile(mentorProfile)
                .title(request.title().trim())
                .description(request.description() == null ? null : request.description().trim())
                .expectedOutcome(request.expectedOutcome() == null ? null : request.expectedOutcome().trim())
                .durationMinutes(request.durationMinutes())
                .isFree(request.isFree())
                .priceScoin(request.isFree() ? 0 : request.priceScoin())
                .maintainPostSessionChat(Boolean.TRUE.equals(request.maintainPostSessionChat()))
                .deliveryMode(request.deliveryMode())
                .isActive(true)
                .build();

        validateServicePricingAndDuration(service.getDurationMinutes(), service.getPriceScoin(), service.isFree());
        MentorService saved = mentorServiceRepository.save(service);
        touchMentorActivity(mentorProfile, DateTimeUtil.now());
        if (applicationEventPublisher != null) {
            applicationEventPublisher.publishEvent(new MentorBookingPolicyUpdatedEvent(mentorUserId));
        }
        return toResponse(saved);
    }

    @Transactional
    public MentorServiceManagementResponse updateService(UUID mentorUserId, UUID serviceId, UpdateMentorServiceRequest request) {
        MentorProfile mentorProfile = requireEligibleMentorProfile(mentorUserId);
        requireRequest(request);

        MentorService service = loadOwnedService(mentorProfile.getUserId(), serviceId);
        if (!java.util.Objects.equals(request.expectedVersion(), service.getVersion())) {
            throw versionConflict(serviceId, request.expectedVersion(), service.getVersion());
        }

        boolean durationChanged = request.durationMinutes() != null && !request.durationMinutes().equals(service.getDurationMinutes());
        boolean deliveryModeChanged = request.deliveryMode() != null && request.deliveryMode() != service.getDeliveryMode();
        if (request.title() != null) {
            service.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            service.setDescription(request.description().trim());
        }
        if (request.expectedOutcome() != null) {
            service.setExpectedOutcome(request.expectedOutcome().trim());
        }
        if (request.durationMinutes() != null) {
            service.setDurationMinutes(request.durationMinutes());
        }
        if (request.isFree() != null) {
            service.setFree(request.isFree());
        }
        if (Boolean.TRUE.equals(service.isFree())) {
            service.setPriceScoin(0);
        } else if (request.priceScoin() != null) {
            service.setPriceScoin(request.priceScoin());
        }
        if (request.maintainPostSessionChat() != null) {
            service.setMaintainPostSessionChat(request.maintainPostSessionChat());
        }
        if (request.deliveryMode() != null) {
            service.setDeliveryMode(request.deliveryMode());
        }

        validateServicePricingAndDuration(service.getDurationMinutes(), service.getPriceScoin(), service.isFree());
        if (durationChanged || deliveryModeChanged) {
            bookingAvailabilityPort.unpublishSlotsForService(service.getId());
        }

        MentorService saved = mentorServiceRepository.save(service);
        touchMentorActivity(mentorProfile, DateTimeUtil.now());
        if (applicationEventPublisher != null) {
            applicationEventPublisher.publishEvent(new MentorBookingPolicyUpdatedEvent(mentorUserId));
        }
        return toResponse(saved);
    }

    @Transactional
    public MentorServiceManagementResponse toggleActive(UUID mentorUserId, UUID serviceId, MentorServiceActiveRequest request) {
        MentorProfile mentorProfile = requireEligibleMentorProfile(mentorUserId);
        if (request == null || request.isActive() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu dữ liệu isActive");
        }
        MentorService service = loadOwnedService(mentorProfile.getUserId(), serviceId);
        if (!java.util.Objects.equals(request.expectedVersion(), service.getVersion())) {
            throw versionConflict(serviceId, request.expectedVersion(), service.getVersion());
        }
        if (Boolean.TRUE.equals(request.isActive()) && Boolean.TRUE.equals(request.rejectPendingBookings())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "rejectPendingBookings chỉ hợp lệ khi deactivate service");
        }
        if (!Boolean.TRUE.equals(request.isActive())) {
            if (bookingAvailabilityPort.hasPendingFutureBookingsForService(serviceId) && !Boolean.TRUE.equals(request.rejectPendingBookings())) {
                throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "SERVICE_HAS_PENDING_BOOKINGS");
            }
            bookingAvailabilityPort.rejectPendingBookingsForService(serviceId, "MENTOR_SERVICE_DEACTIVATED");
            bookingAvailabilityPort.unbindFutureSlotsForService(serviceId);
        }
        service.setActive(request.isActive());
        touchMentorActivity(mentorProfile, DateTimeUtil.now());
        MentorServiceManagementResponse response = toResponse(mentorServiceRepository.save(service));
        if (applicationEventPublisher != null) {
            applicationEventPublisher.publishEvent(new MentorBookingPolicyUpdatedEvent(mentorUserId));
        }
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
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã dịch vụ không hợp lệ");
        }
        return mentorServiceRepository.findByIdAndMentorProfileUserId(serviceId, mentorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dịch vụ mentoring"));
    }

    private void touchMentorActivity(MentorProfile mentorProfile, LocalDateTime at) {
        mentorProfile.setLastActivityAt(at);
        mentorProfileRepository.save(mentorProfile);
    }

    private void validateServicePricingAndDuration(Integer durationMinutes, Integer priceScoin, Boolean isFree) {
        if (durationMinutes == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thời lượng dịch vụ không được để trống");
        }
        if (!PricingPolicy.isAllowedServiceDuration(durationMinutes, paymentProperties)) {
            throw new BaseException(ErrorCode.BAD_REQUEST,
                    "Thời lượng dịch vụ không hợp lệ. Cho phép: "
                            + PricingPolicy.allowedServiceDurations(paymentProperties));
        }
        if (Boolean.TRUE.equals(isFree)) {
            return;
        }
        PricingPolicy.validatePaidServicePrice(priceScoin, durationMinutes, paymentProperties);
    }

    private MentorServiceManagementResponse toResponse(MentorService service) {
        int basePrice = Boolean.TRUE.equals(service.isFree()) ? 0 : (service.getPriceScoin() == null ? 0 : service.getPriceScoin());
        int publicPrice = PricingPolicy.menteePayableScoin(basePrice, paymentProperties);
        int payout = PricingPolicy.mentorNetScoin(basePrice, paymentProperties);

        return MentorServiceManagementResponse.builder()
                .id(service.getId())
                .title(service.getTitle())
                .description(service.getDescription())
                .expectedOutcome(service.getExpectedOutcome())
                .durationMinutes(service.getDurationMinutes())
                .isFree(service.isFree())
                .basePriceScoin(basePrice)
                .priceScoin(publicPrice)
                .mentorPayoutScoin(payout)
                .isActive(service.isActive())
                .maintainPostSessionChat(service.isMaintainPostSessionChat())
                .deliveryMode(service.getDeliveryMode())
                .version(service.getVersion())
                .createdAt(service.getCreatedAt())
                .updatedAt(service.getUpdatedAt())
                .build();
    }

    private void requireUserId(UUID mentorUserId) {
        if (mentorUserId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "mentorUserId không được để trống");
        }
    }

    private void requireRequest(Object request) {
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Dữ liệu yêu cầu không được để trống");
        }
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
}
