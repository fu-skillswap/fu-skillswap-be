package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.domain.TagStatus;
import com.fptu.exe.skillswap.modules.catalog.domain.TagType;
import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilitySlotService;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.repository.AvailabilitySlotServiceRepository;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceDeliveryMode;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorServiceResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorServiceConstraintsResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.request.CreateMentorServiceRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.request.UpdateMentorServiceRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorServiceActiveRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorServiceUpsertRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorTagResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import com.fptu.exe.skillswap.shared.exception.VersionConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MentorServiceManagementService {

    private static final Set<Integer> ALLOWED_DURATIONS = Set.of(15, 30, 60, 90);
    private static final int MIN_PRICE_SCOIN_PER_MINUTE = 1_200;
    private static final int MAX_PRICE_SCOIN_PER_MINUTE = 500_000;

    private final MentorServiceRepository mentorServiceRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final MentorProfileService mentorProfileService;
    private final BookingRepository bookingRepository;
    private final AvailabilitySlotServiceRepository availabilitySlotServiceRepository;
    private final MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;

    @Transactional(readOnly = true)
    public List<MentorServiceResponse> getMyServices(UUID mentorUserId, Boolean isActive) {
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
                ALLOWED_DURATIONS,
                MIN_PRICE_SCOIN_PER_MINUTE,
                MAX_PRICE_SCOIN_PER_MINUTE
        );
    }

    /** Java-only compatibility bridge for the removed `active=all` query convention. */
    @Deprecated(forRemoval = true)
    public List<MentorServiceResponse> getMyServices(UUID mentorUserId, String active) {
        if (active == null || active.isBlank() || "all".equalsIgnoreCase(active)) {
            return getMyServices(mentorUserId, (Boolean) null);
        }
        if ("true".equalsIgnoreCase(active) || "false".equalsIgnoreCase(active)) {
            return getMyServices(mentorUserId, Boolean.valueOf(active));
        }
        throw new BaseException(ErrorCode.BAD_REQUEST, "Query param active chỉ chấp nhận true, false hoặc all");
    }

    @Transactional(readOnly = true)
    public MentorServiceResponse getMyServiceDetail(UUID mentorUserId, UUID serviceId) {
        MentorProfile mentorProfile = requireEligibleMentorProfile(mentorUserId);
        return toResponse(loadOwnedService(mentorProfile.getUserId(), serviceId));
    }

    @Transactional
    public MentorServiceResponse createService(UUID mentorUserId, CreateMentorServiceRequest request) {
        MentorProfile mentorProfile = requireEligibleMentorProfile(mentorUserId);
        requireRequest(request);

        List<Tag> helpTopics = loadHelpTopics(request.helpTopicIds());
        int durationMinutes = validateDuration(request.durationMinutes());
        boolean isFree = Boolean.TRUE.equals(request.isFree());
        MentorService service = MentorService.builder()
                .mentorProfile(mentorProfile)
                .title(cleanRequired(request.title(), "Tiêu đề dịch vụ"))
                .description(cleanRequired(request.description(), "Mô tả dịch vụ"))
                .expectedOutcome(cleanRequired(request.expectedOutcome(), "Kết quả kỳ vọng"))
                .durationMinutes(durationMinutes)
                .isFree(isFree)
                .priceScoin(normalizePriceScoin(isFree, request.priceScoin(), durationMinutes))
                .isActive(true)
                .maintainPostSessionChat(Boolean.TRUE.equals(request.maintainPostSessionChat()))
                .deliveryMode(request.deliveryMode() == null ? MentorServiceDeliveryMode.ONE_TO_ONE : request.deliveryMode())
                .helpTopics(new LinkedHashSet<>(helpTopics))
                .build();

        touchMentorActivity(mentorProfile, LocalDateTime.now());
        return toResponse(mentorServiceRepository.save(service));
    }

    /** Java-only bridge; the HTTP endpoint uses CreateMentorServiceRequest. */
    @Deprecated(forRemoval = true)
    public MentorServiceResponse createService(UUID mentorUserId, MentorServiceUpsertRequest request) {
        return createService(mentorUserId, new CreateMentorServiceRequest(
                request.title(), request.description(), request.expectedOutcome(), request.durationMinutes(),
                request.isFree(), request.priceScoin(), false, MentorServiceDeliveryMode.ONE_TO_ONE, request.helpTopicIds()
        ));
    }

    @Transactional
    public MentorServiceResponse updateService(UUID mentorUserId, UUID serviceId, UpdateMentorServiceRequest request) {
        MentorProfile mentorProfile = requireEligibleMentorProfile(mentorUserId);
        requireRequest(request);

        MentorService service = loadOwnedService(mentorProfile.getUserId(), serviceId);
        List<Tag> helpTopics = loadHelpTopics(request.helpTopicIds());
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
        replaceHelpTopics(service, helpTopics);
        touchMentorActivity(mentorProfile, LocalDateTime.now());

        return toResponse(mentorServiceRepository.save(service));
    }

    @Transactional
    public MentorServiceResponse changeActiveStatus(UUID mentorUserId, UUID serviceId, MentorServiceActiveRequest request) {
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
        if (!Boolean.TRUE.equals(request.isActive())) {
            LocalDateTime now = LocalDateTime.now();
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
                mentorAvailabilitySlotRepository.bumpVersions(changedSlotIds, now);
            }
        }
        service.setActive(request.isActive());
        touchMentorActivity(mentorProfile, LocalDateTime.now());
        return toResponse(mentorServiceRepository.save(service));
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

    private List<Tag> loadHelpTopics(List<UUID> helpTopicIds) {
        if (helpTopicIds == null || helpTopicIds.isEmpty()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Danh sách chủ đề hỗ trợ không được để trống");
        }

        Set<UUID> uniqueIds = new LinkedHashSet<>(helpTopicIds);
        if (uniqueIds.size() != helpTopicIds.size()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Danh sách chủ đề hỗ trợ không được trùng lặp");
        }

        List<Tag> tags = tagRepository.findByIdInAndStatus(uniqueIds, TagStatus.ACTIVE);
        if (tags.size() != uniqueIds.size()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Một hoặc nhiều chủ đề hỗ trợ không tồn tại hoặc chưa được duyệt");
        }

        boolean invalidType = tags.stream().anyMatch(tag -> tag.getType() != TagType.HELP_TOPIC);
        if (invalidType) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Một hoặc nhiều chủ đề hỗ trợ không đúng loại HELP_TOPIC");
        }
        return tags;
    }

    private void replaceHelpTopics(MentorService service, List<Tag> helpTopics) {
        service.getHelpTopics().clear();
        service.getHelpTopics().addAll(helpTopics);
    }

    private MentorServiceResponse toResponse(MentorService service) {
        List<MentorTagResponse> helpTopics = service.getHelpTopics().stream()
                .sorted(Comparator.comparing(tag -> tag.getNameVi() == null ? "" : tag.getNameVi()))
                .map(this::toTagResponse)
                .toList();

        return MentorServiceResponse.builder()
                .serviceId(service.getId())
                .mentorUserId(service.getMentorProfile() == null ? null : service.getMentorProfile().getUserId())
                .title(service.getTitle())
                .description(service.getDescription())
                .expectedOutcome(service.getExpectedOutcome())
                .durationMinutes(service.getDurationMinutes())
                .isFree(service.isFree())
                .priceScoin(service.isFree() ? 0 : defaultInteger(service.getPriceScoin()))
                .isActive(service.isActive())
                .maintainPostSessionChat(service.isMaintainPostSessionChat())
                .deliveryMode(service.getDeliveryMode())
                .version(service.getVersion())
                .helpTopics(helpTopics)
                .createdAt(service.getCreatedAt())
                .updatedAt(service.getUpdatedAt())
                .build();
    }

    private MentorTagResponse toTagResponse(Tag tag) {
        return MentorTagResponse.builder()
                .id(tag.getId())
                .code(tag.getCode())
                .nameVi(tag.getNameVi())
                .nameEn(tag.getNameEn())
                .type(tag.getType())
                .primary(false)
                .build();
    }

    private Integer validateDuration(Integer durationMinutes) {
        if (durationMinutes == null || !ALLOWED_DURATIONS.contains(durationMinutes)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thời lượng dịch vụ chỉ được chọn 15, 30, 60 hoặc 90 phút");
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

        if (priceScoin == null || priceScoin <= 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Dịch vụ có phí phải có priceScoin lớn hơn 0");
        }
        int minPrice = minimumPriceForDuration(durationMinutes);
        if (priceScoin < minPrice) {
            throw new BaseException(
                    ErrorCode.BAD_REQUEST,
                    "Dịch vụ có phí phải có giá tối thiểu " + minPrice + " SCoin cho " + durationMinutes + " phút"
            );
        }
        int maxPrice = maximumPriceForDuration(durationMinutes);
        if (priceScoin > maxPrice) {
            throw new BaseException(
                    ErrorCode.BAD_REQUEST,
                    "Dịch vụ có phí chỉ được đặt tối đa " + maxPrice + " SCoin cho " + durationMinutes + " phút"
            );
        }
        return priceScoin;
    }

    private int minimumPriceForDuration(Integer durationMinutes) {
        if (durationMinutes == null || durationMinutes <= 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thời lượng dịch vụ không hợp lệ");
        }
        return durationMinutes * MIN_PRICE_SCOIN_PER_MINUTE;
    }

    private int maximumPriceForDuration(Integer durationMinutes) {
        if (durationMinutes == null || durationMinutes <= 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thời lượng dịch vụ không hợp lệ");
        }
        return durationMinutes * MAX_PRICE_SCOIN_PER_MINUTE;
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
        LocalDateTime rejectedAt = LocalDateTime.now();
        for (Booking booking : bookings) {
            if (booking.getStatus() != BookingStatus.PENDING) {
                continue;
            }
            booking.setStatus(BookingStatus.REJECTED);
            booking.setRejectReason(reason);
            booking.setRejectedAt(rejectedAt);
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
