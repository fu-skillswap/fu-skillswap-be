package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.academic.domain.AcademicProgram;
import com.fptu.exe.skillswap.modules.academic.domain.Campus;
import com.fptu.exe.skillswap.modules.academic.domain.Specialization;
import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTag;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagType;
import com.fptu.exe.skillswap.modules.catalog.repository.MentorTagRepository;
import com.fptu.exe.skillswap.modules.feedback.dto.MentorReviewResponse;
import com.fptu.exe.skillswap.modules.feedback.repository.SessionFeedbackRepository;
import com.fptu.exe.skillswap.modules.feedback.repository.query.MentorReviewQueryRow;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorAvailabilitySlotResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorDiscoveryCardResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorDiscoveryDetailResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorDiscoverySearchRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorPublicServiceResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorRecommendationResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorTagResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MentorDiscoveryService {

    private static final UUID EMPTY_TAG_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal SAME_SPECIALIZATION_SCORE = decimal(40);
    private static final BigDecimal SAME_PROGRAM_SCORE = decimal(18);
    private static final BigDecimal SAME_CAMPUS_SCORE = decimal(10);
    private static final BigDecimal MENTOR_ALUMNI_SCORE = decimal(30);
    private static final BigDecimal MENTOR_HIGHER_SEMESTER_SCORE = decimal(20);
    private static final BigDecimal MENTOR_EQUAL_SEMESTER_SCORE = decimal(10);
    private static final BigDecimal NEWLY_ACTIVE_SCORE = decimal(10);
    private static final BigDecimal TENURE_STEP_SCORE = decimal("0.2");
    private static final BigDecimal COMPLETED_SESSION_SCORE = decimal("0.1");
    private static final BigDecimal MAX_RATING = decimal(5);

    private final MentorProfileRepository mentorProfileRepository;
    private final MentorTagRepository mentorTagRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final MentorServiceRepository mentorServiceRepository;
    private final MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;
    private final SessionFeedbackRepository sessionFeedbackRepository;

    @Transactional(readOnly = true)
    public PageResponse<MentorDiscoveryCardResponse> searchMentors(UUID currentUserId, MentorDiscoverySearchRequest request) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }

        MentorDiscoverySearchRequest safeRequest = request == null ? new MentorDiscoverySearchRequest() : request;
        List<UUID> tagIds = normalizedTagIds(safeRequest.getTagIds());

        StudentProfile menteeProfile = studentProfileRepository.findWithDetailsByUserId(currentUserId).orElse(null);
        UUID menteeCampusId = menteeProfile != null && menteeProfile.getCampus() != null ? menteeProfile.getCampus().getId() : null;
        UUID menteeProgramId = menteeProfile != null && menteeProfile.getProgram() != null ? menteeProfile.getProgram().getId() : null;
        UUID menteeSpecializationId = menteeProfile != null && menteeProfile.getSpecialization() != null ? menteeProfile.getSpecialization().getId() : null;
        Integer menteeSemester = menteeProfile != null ? menteeProfile.getSemester() : null;
        LocalDateTime now = LocalDateTime.now();

        Page<MentorDiscoveryQueryRow> page;
        if (isRelevanceSort(safeRequest.getSortBy())) {
            Pageable pageable = PageRequest.of(Math.max(safeRequest.getPage(), 0), Math.min(Math.max(safeRequest.getSize(), 1), 30));
            page = mentorProfileRepository.searchDiscoverableMentorsSortedByRelevance(
                    MentorStatus.ACTIVE,
                    MentorTagType.HELP_TOPIC,
                    normalizeKeyword(safeRequest.getKeyword()),
                    safeRequest.getCampusId(),
                    safeRequest.getSpecializationId(),
                    safeRequest.getTeachingMode(),
                    safeRequest.getIsAvailable(),
                    hasTagFilter(safeRequest.getTagIds()),
                    tagIds,
                    menteeCampusId,
                    menteeProgramId,
                    menteeSpecializationId,
                    menteeSemester,
                    now,
                    pageable
            );
        } else {
            page = mentorProfileRepository.searchDiscoverableMentors(
                    MentorStatus.ACTIVE,
                    MentorTagType.HELP_TOPIC,
                    normalizeKeyword(safeRequest.getKeyword()),
                    safeRequest.getCampusId(),
                    safeRequest.getSpecializationId(),
                    safeRequest.getTeachingMode(),
                    safeRequest.getIsAvailable(),
                    hasTagFilter(safeRequest.getTagIds()),
                    tagIds,
                    menteeCampusId,
                    menteeProgramId,
                    menteeSpecializationId,
                    menteeSemester,
                    now,
                    searchPageable(safeRequest)
            );
        }

        Map<UUID, List<MentorTagResponse>> helpTopicsByMentor = loadTagsByMentor(
                page.getContent().stream().map(MentorDiscoveryQueryRow::mentorUserId).toList(),
                Set.of(MentorTagType.HELP_TOPIC)
        );

        List<MentorDiscoveryCardResponse> content = page.getContent().stream()
                .map(row -> toCardResponse(row, helpTopicsByMentor.getOrDefault(row.mentorUserId(), List.of())))
                .toList();

        return PageResponse.<MentorDiscoveryCardResponse>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public List<MentorRecommendationResponse> getRecommendations(UUID currentUserId, int limit) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }

        int safeLimit = Math.min(Math.max(limit, 1), 12);
        StudentProfile menteeProfile = studentProfileRepository.findWithDetailsByUserId(currentUserId).orElse(null);
        UUID menteeCampusId = menteeProfile != null && menteeProfile.getCampus() != null ? menteeProfile.getCampus().getId() : null;
        UUID menteeProgramId = menteeProfile != null && menteeProfile.getProgram() != null ? menteeProfile.getProgram().getId() : null;
        UUID menteeSpecializationId = menteeProfile != null && menteeProfile.getSpecialization() != null ? menteeProfile.getSpecialization().getId() : null;
        Integer menteeSemester = menteeProfile != null ? menteeProfile.getSemester() : null;
        LocalDateTime now = LocalDateTime.now();

        List<MentorDiscoveryQueryRow> candidates = mentorProfileRepository.findRecommendationCandidatesSortedByRelevance(
                MentorStatus.ACTIVE,
                MentorTagType.HELP_TOPIC,
                currentUserId,
                menteeCampusId,
                menteeProgramId,
                menteeSpecializationId,
                menteeSemester,
                now,
                PageRequest.of(0, safeLimit)
        );

        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<MentorTagResponse>> helpTopicsByMentor = loadTagsByMentor(
                candidates.stream().map(MentorDiscoveryQueryRow::mentorUserId).toList(),
                Set.of(MentorTagType.HELP_TOPIC)
        );

        return candidates.stream()
                .map(candidate -> toRecommendation(candidate, helpTopicsByMentor.getOrDefault(candidate.mentorUserId(), List.of()), menteeProfile))
                .toList();
    }

    @Transactional(readOnly = true)
    public MentorDiscoveryDetailResponse getMentorDetail(UUID mentorUserId) {
        MentorProfile mentorProfile = getDiscoverableMentorProfile(mentorUserId);
        StudentProfile studentProfile = studentProfileRepository.findWithDetailsByUserId(mentorUserId).orElse(null);
        Map<UUID, List<MentorTagResponse>> tagsByMentor = loadTagsByMentor(
                List.of(mentorUserId),
                Set.of(MentorTagType.HELP_TOPIC)
        );
        List<MentorTagResponse> mentorTags = tagsByMentor.getOrDefault(mentorUserId, List.of());
        List<MentorPublicServiceResponse> services = mentorServiceRepository
                .findByMentorProfileUserIdAndIsActiveTrueOrderByCreatedAtAsc(mentorUserId)
                .stream()
                .map(this::toPublicServiceResponse)
                .toList();

        Campus campus = studentProfile == null ? null : studentProfile.getCampus();
        AcademicProgram program = studentProfile == null ? null : studentProfile.getProgram();
        Specialization specialization = studentProfile == null ? null : studentProfile.getSpecialization();

        BigDecimal rating = defaultDecimal(mentorProfile.getAverageRating());
        int reviews = defaultInteger(mentorProfile.getTotalReviews());
        BigDecimal displayRating = reviews == 0 ? BigDecimal.valueOf(5.0).setScale(2, RoundingMode.HALF_UP) : rating;

        return MentorDiscoveryDetailResponse.builder()
                .mentorUserId(mentorProfile.getUserId())
                .displayName(mentorProfile.getUser().getFullName())
                .avatarUrl(mentorProfile.getUser().getAvatarUrl())
                .headline(mentorProfile.getHeadline())
                .bio(studentProfile == null ? null : studentProfile.getBio())
                .expertiseDescription(mentorProfile.getExpertiseDescription())
                .supportingSubjects(mentorProfile.getSupportingSubjects())
                .isAvailable(mentorProfile.isAvailable())
                .ratingAverage(displayRating)
                .reviewCount(reviews)
                .completedSessions(defaultInteger(mentorProfile.getTotalCompletedSessions()))
                .teachingMode(mentorProfile.getTeachingMode())
                .defaultSessionDuration(mentorProfile.getSessionDuration())
                .verifiedAt(mentorProfile.getVerifiedAt())
                .campusId(campus == null ? null : campus.getId())
                .campusName(campus == null ? null : campus.getName())
                .programId(program == null ? null : program.getId())
                .programName(program == null ? null : program.getNameVi())
                .specializationId(specialization == null ? null : specialization.getId())
                .specializationName(specialization == null ? null : specialization.getNameVi())
                .semester(studentProfile == null ? null : studentProfile.getSemester())
                .alumni(studentProfile != null && studentProfile.isAlumni())
                .portfolioUrl(mentorProfile.getPortfolioUrl())
                .linkedinUrl(mentorProfile.getLinkedinUrl())
                .githubUrl(mentorProfile.getGithubUrl())
                .helpTopicTags(filterTagsByType(mentorTags, MentorTagType.HELP_TOPIC))
                .services(services)
                .build();
    }

    @Transactional(readOnly = true)
    public List<MentorAvailabilitySlotResponse> getMentorAvailability(UUID mentorUserId) {
        MentorProfile mentorProfile = getDiscoverableMentorProfile(mentorUserId);
        return mentorAvailabilitySlotRepository
                .findByMentorProfileUserIdAndIsActiveTrueAndIsBookedFalseAndStartTimeAfterOrderByStartTimeAsc(
                        mentorUserId,
                        LocalDateTime.now()
                )
                .stream()
                .limit(60)
                .map(slot -> toAvailabilityResponse(slot, mentorProfile))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<MentorReviewResponse> getMentorReviews(UUID mentorUserId, BasePageRequest pageRequest) {
        getDiscoverableMentorProfile(mentorUserId);

        BasePageRequest safeRequest = pageRequest == null ? new BasePageRequest() : pageRequest;
        Page<MentorReviewQueryRow> page = sessionFeedbackRepository.findPublicMentorReviews(
                mentorUserId,
                reviewPageable(safeRequest)
        );

        return PageResponse.<MentorReviewResponse>builder()
                .content(page.getContent().stream().map(this::toMentorReviewResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    private MentorRecommendationResponse toRecommendation(
            MentorDiscoveryQueryRow candidate,
            List<MentorTagResponse> helpTopicTags,
            StudentProfile menteeProfile
    ) {
        List<String> reasons = new ArrayList<>();
        calculateMatchScore(candidate, menteeProfile, reasons);

        if (defaultInteger(candidate.completedSessions()) > 0 && reasons.stream().noneMatch("Đã có nhiều phiên mentoring hoàn thành"::equals)) {
            reasons.add("Đã có phiên mentoring hoàn thành");
        }

        if (reasons.isEmpty()) {
            reasons.add("Phù hợp với các tiêu chí discovery hiện tại");
        }

        BigDecimal score = candidate.matchScore() == null ? ZERO : BigDecimal.valueOf(candidate.matchScore()).setScale(2, RoundingMode.HALF_UP);

        return MentorRecommendationResponse.builder()
                .mentor(toCardResponse(candidate, helpTopicTags))
                .matchScore(score)
                .matchReasons(reasons.stream().limit(3).toList())
                .build();
    }

    private BigDecimal calculateMatchScore(MentorDiscoveryQueryRow candidate, StudentProfile menteeProfile, List<String> reasons) {
        BigDecimal baseScore = ZERO;

        if (menteeProfile != null) {
            if (sameUuid(menteeProfile.getSpecialization() == null ? null : menteeProfile.getSpecialization().getId(), candidate.specializationId())) {
                baseScore = baseScore.add(SAME_SPECIALIZATION_SCORE);
                reasons.add("Cùng chuyên ngành với mentee");
            }
            if (sameUuid(menteeProfile.getProgram() == null ? null : menteeProfile.getProgram().getId(), candidate.programId())) {
                baseScore = baseScore.add(SAME_PROGRAM_SCORE);
                reasons.add("Cùng chương trình học");
            }
            if (sameUuid(menteeProfile.getCampus() == null ? null : menteeProfile.getCampus().getId(), candidate.campusId())) {
                baseScore = baseScore.add(SAME_CAMPUS_SCORE);
                reasons.add("Cùng campus");
            }
            baseScore = baseScore.add(calculateStudentProfileScore(menteeProfile, candidate, reasons));
        }

        baseScore = baseScore.add(calculateActivityScore(candidate, reasons));
        baseScore = baseScore.add(calculateContributionScore(candidate.completedSessions(), reasons));

        BigDecimal ratingFactor = calculateRatingFactor(candidate.ratingAverage(), candidate.reviewCount(), reasons);
        return baseScore.multiply(ratingFactor).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateStudentProfileScore(StudentProfile menteeProfile, MentorDiscoveryQueryRow candidate, List<String> reasons) {
        if (Boolean.TRUE.equals(candidate.alumni())) {
            reasons.add("Mentor là cựu sinh viên");
            return MENTOR_ALUMNI_SCORE;
        }

        Integer menteeSemester = menteeProfile.getSemester();
        Integer mentorSemester = candidate.semester();
        if (menteeSemester == null || mentorSemester == null) {
            return ZERO;
        }
        if (mentorSemester > menteeSemester) {
            reasons.add("Mentor đi trước mentee về học kỳ");
            return MENTOR_HIGHER_SEMESTER_SCORE;
        }
        if (mentorSemester.equals(menteeSemester)) {
            reasons.add("Mentor cùng học kỳ chuyên ngành với mentee");
            return MENTOR_EQUAL_SEMESTER_SCORE;
        }
        return ZERO;
    }

    private BigDecimal calculateActivityScore(MentorDiscoveryQueryRow candidate, List<String> reasons) {
        LocalDateTime verifiedAt = candidate.verifiedAt();
        if (verifiedAt == null) {
            return ZERO;
        }

        long activeDays = Math.max(0, ChronoUnit.DAYS.between(verifiedAt, LocalDateTime.now()));
        if (activeDays < 3) {
            reasons.add("Mentor mới active gần đây");
            return NEWLY_ACTIVE_SCORE;
        }

        BigDecimal tenureScore = TENURE_STEP_SCORE.multiply(BigDecimal.valueOf(activeDays / 7L));
        if (tenureScore.compareTo(ZERO) > 0) {
            reasons.add("Mentor có thời gian đóng góp ổn định");
        }
        return tenureScore;
    }

    private BigDecimal calculateRatingFactor(BigDecimal ratingAverage, Integer reviewCount, List<String> reasons) {
        BigDecimal normalizedRating = defaultDecimal(ratingAverage);
        int normalizedReviewCount = defaultInteger(reviewCount);
        if (normalizedReviewCount <= 0) {
            return BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);
        }
        if (normalizedRating.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }

        if (normalizedRating.compareTo(BigDecimal.valueOf(4.0)) >= 0 && normalizedReviewCount >= 3) {
            reasons.add("Được đánh giá tốt và có độ tin cậy từ review");
        }
        return normalizedRating.divide(MAX_RATING, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateContributionScore(Integer completedSessions, List<String> reasons) {
        int normalizedCompletedSessions = defaultInteger(completedSessions);
        if (normalizedCompletedSessions <= 0) {
            return ZERO;
        }

        if (normalizedCompletedSessions >= 10) {
            reasons.add("Đã có nhiều phiên mentoring hoàn thành");
        }
        return COMPLETED_SESSION_SCORE.multiply(BigDecimal.valueOf(normalizedCompletedSessions));
    }

    private Map<UUID, List<MentorTagResponse>> loadTagsByMentor(Collection<UUID> mentorUserIds, Set<MentorTagType> tagTypes) {
        if (mentorUserIds == null || mentorUserIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<MentorTagResponse>> result = new HashMap<>();
        mentorTagRepository.findByIdMentorUserIdInAndIdTagTypeIn(mentorUserIds, tagTypes)
                .stream()
                .sorted(Comparator
                        .comparing((MentorTag mentorTag) -> mentorTag.getId().getMentorUserId())
                        .thenComparing(mentorTag -> mentorTag.getTag().getNameVi()))
                .forEach(mentorTag -> result.computeIfAbsent(mentorTag.getId().getMentorUserId(), ignored -> new ArrayList<>())
                        .add(toTagResponse(mentorTag)));
        return result;
    }

    private MentorDiscoveryCardResponse toCardResponse(MentorDiscoveryQueryRow row, List<MentorTagResponse> helpTopicTags) {
        BigDecimal rating = defaultDecimal(row.ratingAverage());
        int reviews = defaultInteger(row.reviewCount());
        BigDecimal displayRating = reviews == 0 ? BigDecimal.valueOf(5.0).setScale(2, RoundingMode.HALF_UP) : rating;
        return MentorDiscoveryCardResponse.builder()
                .mentorUserId(row.mentorUserId())
                .displayName(row.displayName())
                .avatarUrl(row.avatarUrl())
                .headline(row.headline())
                .expertiseDescription(row.expertiseDescription())
                .supportingSubjects(row.supportingSubjects())
                .isAvailable(row.isAvailable())
                .ratingAverage(displayRating)
                .reviewCount(reviews)
                .completedSessions(defaultInteger(row.completedSessions()))
                .teachingMode(row.teachingMode())
                .verifiedAt(row.verifiedAt())
                .campusId(row.campusId())
                .campusName(row.campusName())
                .programId(row.programId())
                .programName(row.programName())
                .specializationId(row.specializationId())
                .specializationName(row.specializationName())
                .helpTopicTags(helpTopicTags.stream().limit(5).toList())
                .build();
    }

    private MentorTagResponse toTagResponse(MentorTag mentorTag) {
        return MentorTagResponse.builder()
                .id(mentorTag.getTag().getId())
                .code(mentorTag.getTag().getCode())
                .nameVi(mentorTag.getTag().getNameVi())
                .nameEn(mentorTag.getTag().getNameEn())
                .type(mentorTag.getTag().getType())
                .primary(mentorTag.isPrimary())
                .build();
    }

    private MentorPublicServiceResponse toPublicServiceResponse(MentorService mentorService) {
        return MentorPublicServiceResponse.builder()
                .id(mentorService.getId())
                .title(mentorService.getTitle())
                .description(mentorService.getDescription())
                .durationMinutes(mentorService.getDurationMinutes())
                .free(mentorService.isFree())
                .priceAmount(defaultDecimal(mentorService.getPriceAmount()))
                .currency(mentorService.getCurrency())
                .build();
    }

    private MentorAvailabilitySlotResponse toAvailabilityResponse(MentorAvailabilitySlot slot, MentorProfile mentorProfile) {
        return MentorAvailabilitySlotResponse.builder()
                .slotId(slot.getId())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .timezone(slot.getTimezone())
                .durationMinutes((int) ChronoUnit.MINUTES.between(slot.getStartTime(), slot.getEndTime()))
                .teachingMode(mentorProfile.getTeachingMode())
                .recurring(slot.getRecurrenceRule() != null && !slot.getRecurrenceRule().isBlank())
                .build();
    }

    private MentorReviewResponse toMentorReviewResponse(MentorReviewQueryRow row) {
        return MentorReviewResponse.builder()
                .reviewId(row.reviewId())
                .reviewerUserId(row.reviewerUserId())
                .reviewerDisplayName(row.reviewerDisplayName())
                .reviewerAvatarUrl(row.reviewerAvatarUrl())
                .rating(row.rating())
                .comment(row.comment())
                .createdAt(row.createdAt())
                .build();
    }

    private MentorProfile getDiscoverableMentorProfile(UUID mentorUserId) {
        if (mentorUserId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã mentor không hợp lệ");
        }

        MentorProfile mentorProfile = mentorProfileRepository.findWithUserByUserId(mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy mentor"));
        if (!isDiscoverableMentor(mentorProfile)) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Mentor hiện chưa sẵn sàng hiển thị trên discovery");
        }
        return mentorProfile;
    }

    private boolean isDiscoverableMentor(MentorProfile mentorProfile) {
        return mentorProfile != null
                && mentorProfile.getStatus() == MentorStatus.ACTIVE
                && mentorProfile.getVerifiedAt() != null
                && hasText(mentorProfile.getHeadline())
                && hasText(mentorProfile.getExpertiseDescription())
                && mentorProfile.isAvailable()
                && mentorProfile.getTeachingMode() != null
                && mentorProfile.getSessionDuration() != null;
    }

    private List<MentorTagResponse> filterTagsByType(List<MentorTagResponse> tags, MentorTagType tagType) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        Set<com.fptu.exe.skillswap.modules.catalog.domain.TagType> acceptedTypes = switch (tagType) {
            case EXPERTISE -> EnumSet.noneOf(com.fptu.exe.skillswap.modules.catalog.domain.TagType.class);
            case HELP_TOPIC -> EnumSet.of(com.fptu.exe.skillswap.modules.catalog.domain.TagType.HELP_TOPIC);
        };
        return tags.stream()
                .filter(tag -> tag.type() != null && acceptedTypes.contains(tag.type()))
                .toList();
    }

    private Pageable searchPageable(MentorDiscoverySearchRequest request) {
        int page = Math.max(request.getPage(), 0);
        int size = Math.min(Math.max(request.getSize(), 1), 30);
        Sort.Direction direction = request.getDirection() == null ? Sort.Direction.DESC : request.getDirection();
        String sortBy = request.getSortBy() == null ? "relevance" : request.getSortBy().trim();

        List<Sort.Order> orders = switch (sortBy) {
            case "ratingAverage" -> List.of(
                    new Sort.Order(direction, "averageRating"),
                    new Sort.Order(Sort.Direction.DESC, "totalReviews"),
                    new Sort.Order(Sort.Direction.DESC, "totalCompletedSessions")
            );
            case "reviewCount" -> List.of(
                    new Sort.Order(direction, "totalReviews"),
                    new Sort.Order(Sort.Direction.DESC, "averageRating")
            );
            case "completedSessions" -> List.of(
                    new Sort.Order(direction, "totalCompletedSessions"),
                    new Sort.Order(Sort.Direction.DESC, "averageRating")
            );
            case "updatedAt" -> List.of(
                    new Sort.Order(direction, "updatedAt"),
                    new Sort.Order(Sort.Direction.DESC, "verifiedAt")
            );
            default -> List.of(
                    new Sort.Order(Sort.Direction.DESC, "isAvailable"),
                    new Sort.Order(Sort.Direction.DESC, "averageRating"),
                    new Sort.Order(Sort.Direction.DESC, "totalCompletedSessions"),
                    new Sort.Order(Sort.Direction.DESC, "verifiedAt")
            );
        };
        return PageRequest.of(page, size, Sort.by(orders));
    }

    private Pageable reviewPageable(BasePageRequest request) {
        int page = Math.max(request.getPage(), 0);
        int size = Math.min(Math.max(request.getSize(), 1), 20);
        Sort.Direction direction = request.resolveDirection();
        String sortBy = request.getSortBy() == null ? "createdAt" : request.getSortBy().trim();

        List<Sort.Order> orders = switch (sortBy) {
            case "rating" -> List.of(
                    new Sort.Order(direction, "rating"),
                    new Sort.Order(Sort.Direction.DESC, "createdAt")
            );
            default -> List.of(
                    new Sort.Order(direction, "createdAt"),
                    new Sort.Order(Sort.Direction.DESC, "rating")
            );
        };
        return PageRequest.of(page, size, Sort.by(orders));
    }

    private List<UUID> normalizedTagIds(List<UUID> tagIds) {
        if (!hasTagFilter(tagIds)) {
            return List.of(EMPTY_TAG_ID);
        }
        return tagIds.stream().distinct().toList();
    }

    private boolean hasTagFilter(List<UUID> tagIds) {
        return tagIds != null && !tagIds.isEmpty();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        return keyword.trim();
    }

    private boolean isRelevanceSort(String sortBy) {
        return sortBy == null || sortBy.isBlank() || "relevance".equalsIgnoreCase(sortBy.trim());
    }

    private boolean sameUuid(UUID left, UUID right) {
        return left != null && left.equals(right);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private Integer defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private static BigDecimal decimal(int value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }

    private record RankedMentorCard(
            MentorDiscoveryCardResponse card,
            BigDecimal matchScore
    ) {
    }
}
