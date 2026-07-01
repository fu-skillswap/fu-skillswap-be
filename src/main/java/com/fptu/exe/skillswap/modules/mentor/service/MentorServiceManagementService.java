package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.domain.TagStatus;
import com.fptu.exe.skillswap.modules.catalog.domain.TagType;
import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorServiceResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorServiceUpsertRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorTagResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
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
    private static final String ACTIVE_FILTER_ALL = "all";
    private static final String ACTIVE_FILTER_TRUE = "true";
    private static final String ACTIVE_FILTER_FALSE = "false";

    private final MentorServiceRepository mentorServiceRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<MentorServiceResponse> getMyServices(UUID mentorUserId, String activeFilter) {
        MentorProfile mentorProfile = requireEligibleMentorProfile(mentorUserId);
        ActiveFilterMode filterMode = resolveActiveFilter(activeFilter);
        List<MentorService> services = switch (filterMode) {
            case ALL -> mentorServiceRepository.findByMentorProfileUserIdOrderByCreatedAtAsc(mentorProfile.getUserId());
            case ACTIVE -> mentorServiceRepository.findByMentorProfileUserIdAndIsActiveOrderByCreatedAtAsc(mentorProfile.getUserId(), true);
            case INACTIVE -> mentorServiceRepository.findByMentorProfileUserIdAndIsActiveOrderByCreatedAtAsc(mentorProfile.getUserId(), false);
        };
        return services
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MentorServiceResponse getMyServiceDetail(UUID mentorUserId, UUID serviceId) {
        MentorProfile mentorProfile = requireEligibleMentorProfile(mentorUserId);
        return toResponse(loadOwnedService(mentorProfile.getUserId(), serviceId));
    }

    @Transactional
    public MentorServiceResponse createService(UUID mentorUserId, MentorServiceUpsertRequest request) {
        MentorProfile mentorProfile = requireEligibleMentorProfile(mentorUserId);
        requireRequest(request);

        List<Tag> helpTopics = loadHelpTopics(request.helpTopicIds());
        MentorService service = MentorService.builder()
                .mentorProfile(mentorProfile)
                .title(cleanRequired(request.title(), "Tiêu đề dịch vụ"))
                .description(cleanRequired(request.description(), "Mô tả dịch vụ"))
                .expectedOutcome(cleanRequired(request.expectedOutcome(), "Kết quả kỳ vọng"))
                .durationMinutes(validateDuration(request.durationMinutes()))
                .isFree(Boolean.TRUE.equals(request.isFree()))
                .priceScoin(normalizePriceScoin(request.isFree(), request.priceScoin()))
                .isActive(true)
                .helpTopics(new LinkedHashSet<>(helpTopics))
                .build();

        touchMentorActivity(mentorProfile, LocalDateTime.now());
        return toResponse(mentorServiceRepository.save(service));
    }

    @Transactional
    public MentorServiceResponse updateService(UUID mentorUserId, UUID serviceId, MentorServiceUpsertRequest request) {
        MentorProfile mentorProfile = requireEligibleMentorProfile(mentorUserId);
        requireRequest(request);

        MentorService service = loadOwnedService(mentorProfile.getUserId(), serviceId);
        List<Tag> helpTopics = loadHelpTopics(request.helpTopicIds());

        service.setTitle(cleanRequired(request.title(), "Tiêu đề dịch vụ"));
        service.setDescription(cleanRequired(request.description(), "Mô tả dịch vụ"));
        service.setExpectedOutcome(cleanRequired(request.expectedOutcome(), "Kết quả kỳ vọng"));
        service.setDurationMinutes(validateDuration(request.durationMinutes()));
        service.setFree(Boolean.TRUE.equals(request.isFree()));
        service.setPriceScoin(normalizePriceScoin(request.isFree(), request.priceScoin()));
        replaceHelpTopics(service, helpTopics);
        touchMentorActivity(mentorProfile, LocalDateTime.now());

        return toResponse(mentorServiceRepository.save(service));
    }

    @Transactional
    public MentorServiceResponse changeActiveStatus(UUID mentorUserId, UUID serviceId, Boolean active) {
        MentorProfile mentorProfile = requireEligibleMentorProfile(mentorUserId);
        if (active == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Trạng thái active không được để trống");
        }

        MentorService service = loadOwnedService(mentorProfile.getUserId(), serviceId);
        service.setActive(active);
        touchMentorActivity(mentorProfile, LocalDateTime.now());
        return toResponse(mentorServiceRepository.save(service));
    }

    @Transactional
    public MentorServiceResponse deleteService(UUID mentorUserId, UUID serviceId) {
        MentorProfile mentorProfile = requireEligibleMentorProfile(mentorUserId);
        MentorService service = loadOwnedService(mentorProfile.getUserId(), serviceId);
        service.setActive(false);
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
        if (!hasText(profile.getHeadline())
                || !hasText(profile.getExpertiseDescription())
                || profile.getTeachingMode() == null
                || profile.getSessionDuration() == null) {
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
                .free(service.isFree())
                .priceScoin(defaultInteger(service.getPriceScoin()))
                .active(service.isActive())
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
        if (!ALLOWED_DURATIONS.contains(durationMinutes)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thời lượng dịch vụ chỉ được chọn 15, 30, 60 hoặc 90 phút");
        }
        return durationMinutes;
    }

    private Integer normalizePriceScoin(Boolean isFree, Integer priceScoin) {
        if (Boolean.TRUE.equals(isFree)) {
            if (priceScoin != null && priceScoin > 0) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Dịch vụ miễn phí phải có priceScoin bằng 0");
            }
            return 0;
        }

        if (priceScoin == null || priceScoin <= 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Dịch vụ có phí phải có priceScoin lớn hơn 0");
        }
        return priceScoin;
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

    private void requireRequest(MentorServiceUpsertRequest request) {
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

    private void touchMentorActivity(MentorProfile profile, LocalDateTime activityTime) {
        if (profile == null || activityTime == null) {
            return;
        }
        if (profile.getLastActiveAt() == null || profile.getLastActiveAt().isBefore(activityTime)) {
            profile.setLastActiveAt(activityTime);
        }
    }

    private ActiveFilterMode resolveActiveFilter(String activeFilter) {
        String normalized = activeFilter == null ? ACTIVE_FILTER_ALL : activeFilter.trim().toLowerCase();
        return switch (normalized) {
            case "", ACTIVE_FILTER_ALL -> ActiveFilterMode.ALL;
            case ACTIVE_FILTER_TRUE -> ActiveFilterMode.ACTIVE;
            case ACTIVE_FILTER_FALSE -> ActiveFilterMode.INACTIVE;
            default -> throw new BaseException(
                    ErrorCode.BAD_REQUEST,
                    "Query param active chỉ chấp nhận true, false hoặc all"
            );
        };
    }

    private enum ActiveFilterMode {
        ALL,
        ACTIVE,
        INACTIVE
    }
}
