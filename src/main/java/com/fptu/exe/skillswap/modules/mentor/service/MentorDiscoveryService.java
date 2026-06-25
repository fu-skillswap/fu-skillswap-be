package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.academic.domain.AcademicProgram;
import com.fptu.exe.skillswap.modules.academic.domain.Campus;
import com.fptu.exe.skillswap.modules.academic.domain.Specialization;
import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.booking.dto.request.AvailabilityQueryRequest;
import com.fptu.exe.skillswap.modules.booking.service.MentorAvailabilityService;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTag;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagType;
import com.fptu.exe.skillswap.modules.catalog.repository.MentorTagRepository;
import com.fptu.exe.skillswap.modules.feedback.dto.response.MentorReviewResponse;
import com.fptu.exe.skillswap.modules.feedback.repository.SessionFeedbackRepository;
import com.fptu.exe.skillswap.modules.feedback.repository.query.MentorReviewQueryRow;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAvailabilitySlotResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorDiscoveryCardResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorDiscoveryDetailResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorDiscoverySearchRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorRecommendationResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorServiceResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.ServiceSlotCandidatesResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorTagResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MentorDiscoveryService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final UUID EMPTY_TAG_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final String ACCENTED_CHARACTERS = "àáạảãăắằẳẵặâấầẩẫậđèéẹẻẽêếềểễệìíịỉĩòóọỏõôốồổỗộơớờởỡợùúụủũưứừửữựỳýỵỷỹ";
    private static final String PLAIN_CHARACTERS = "aaaaaaaaaaaaaaaaadeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyy";
    private static final BigDecimal SAME_PROGRAM_SCORE = decimal(40);
    private static final BigDecimal SAME_SPECIALIZATION_SCORE = decimal(30);
    private static final BigDecimal SAME_CAMPUS_SCORE = decimal(10);
    private static final BigDecimal MENTOR_ALUMNI_SCORE = decimal(20);
    private static final BigDecimal MENTOR_HIGHER_SEMESTER_SCORE = decimal(15);
    private static final BigDecimal MENTOR_EQUAL_SEMESTER_SCORE = decimal(10);
    private static final BigDecimal HEADLINE_EXACT_BONUS = decimal(60);
    private static final BigDecimal SUBJECTS_PHRASE_BONUS = decimal(40);
    private static final BigDecimal VERIFIED_RECENT_7D_BONUS = decimal(10);
    private static final BigDecimal VERIFIED_RECENT_30D_BONUS = decimal(5);
    private static final BigDecimal SESSION_VELOCITY_HIGH_BONUS = decimal(10);
    private static final BigDecimal SESSION_VELOCITY_MED_BONUS = decimal(5);
    private static final BigDecimal QUALITY_HIGH_RATING_BONUS = decimal(8);
    private static final BigDecimal QUALITY_CREDIBLE_REVIEWS_BONUS = decimal(5);
    private static final BigDecimal QUALITY_EXPERIENCED_SESSIONS_BONUS = decimal(5);

    private final MentorProfileRepository mentorProfileRepository;
    private final MentorTagRepository mentorTagRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final MentorServiceRepository mentorServiceRepository;
    private final MentorAvailabilityService mentorAvailabilityService;
    private final SessionFeedbackRepository sessionFeedbackRepository;
    private final DataSource dataSource;

    private volatile Boolean postgresDetected = null;

    @Transactional(readOnly = true)
    public PageResponse<MentorDiscoveryCardResponse> searchMentors(UUID currentUserId, MentorDiscoverySearchRequest request) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }

        MentorDiscoverySearchRequest safeRequest = request == null ? new MentorDiscoverySearchRequest() : request;
        StudentProfile menteeProfile = loadStudentProfileSafely(currentUserId);
        List<UUID> tagIds = normalizedTagIds(safeRequest.getTagIds());

        boolean hasKeyword = safeRequest.getKeyword() != null && !safeRequest.getKeyword().isBlank();
        String normalizedKeyword = normalizeSearchText(safeRequest.getKeyword());
        String keywordPattern = toLikePattern(safeRequest.getKeyword());
        String normalizedKeywordPattern = toLikePattern(normalizedKeyword);

        int requestedPage = Math.max(safeRequest.getPage(), 0);
        int requestedSize = Math.min(Math.max(safeRequest.getSize(), 1), 30);
        org.springframework.data.domain.Sort.Direction direction = safeRequest.getDirection() == org.springframework.data.domain.Sort.Direction.ASC ? org.springframework.data.domain.Sort.Direction.ASC : org.springframework.data.domain.Sort.Direction.DESC;
        String sortBy = safeRequest.getSortBy() == null ? "relevance" : safeRequest.getSortBy().trim();

        List<org.springframework.data.domain.Sort.Order> orders = new java.util.ArrayList<>();
        switch (sortBy) {
            case "ratingAverage" -> {
                orders.add(new org.springframework.data.domain.Sort.Order(direction, "averageRating"));
                orders.add(new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.DESC, "totalCompletedSessions"));
                orders.add(new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"));
                orders.add(new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.ASC, "userId"));
            }
            case "reviewCount" -> {
                orders.add(new org.springframework.data.domain.Sort.Order(direction, "totalReviews"));
                orders.add(new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.DESC, "averageRating"));
                orders.add(new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.DESC, "totalCompletedSessions"));
                orders.add(new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.ASC, "userId"));
            }
            case "completedSessions" -> {
                orders.add(new org.springframework.data.domain.Sort.Order(direction, "totalCompletedSessions"));
                orders.add(new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.DESC, "averageRating"));
                orders.add(new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.ASC, "userId"));
            }
            case "updatedAt" -> {
                orders.add(new org.springframework.data.domain.Sort.Order(direction, "verifiedAt"));
                orders.add(new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.DESC, "averageRating"));
                orders.add(new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.ASC, "userId"));
            }
            default -> {
                orders.add(new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.DESC, "averageRating"));
                orders.add(new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.DESC, "totalCompletedSessions"));
                orders.add(new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"));
                orders.add(new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.ASC, "userId"));
            }
        }
        boolean relevanceSort = "relevance".equals(sortBy);
        Pageable searchPageable = relevanceSort
                ? PageRequest.of(0, relevanceWindowSize(requestedPage, requestedSize), org.springframework.data.domain.Sort.by(orders))
                : PageRequest.of(requestedPage, requestedSize, org.springframework.data.domain.Sort.by(orders));

        Page<UUID> candidatePage;
        if (hasKeyword && isPostgresDataSource()) {
            candidatePage = findCandidatesByFts(
                    normalizedKeyword,
                    safeRequest,
                    tagIds,
                    requestedPage,
                    requestedSize,
                    relevanceSort
            );
        } else if (hasKeyword) {
            candidatePage = mentorProfileRepository.findDiscoverableCandidateIdsWithKeyword(
                    MentorStatus.ACTIVE,
                    MentorTagType.HELP_TOPIC,
                    safeRequest.getCampusId(),
                    safeRequest.getSpecializationId(),
                    safeRequest.getTeachingMode(),
                    hasTagFilter(safeRequest.getTagIds()),
                    tagIds,
                    keywordPattern,
                    normalizedKeywordPattern,
                    ACCENTED_CHARACTERS,
                    PLAIN_CHARACTERS,
                    currentTime(),
                    searchPageable
            );
        } else {
            candidatePage = mentorProfileRepository.findDiscoverableCandidateIds(
                    MentorStatus.ACTIVE,
                    MentorTagType.HELP_TOPIC,
                    safeRequest.getCampusId(),
                    safeRequest.getSpecializationId(),
                    safeRequest.getTeachingMode(),
                    hasTagFilter(safeRequest.getTagIds()),
                    tagIds,
                    currentTime(),
                    searchPageable
            );
        }

        List<UUID> candidateIds = candidatePage.getContent();
        List<MentorDiscoveryQueryRow> rows = relevanceSort
                ? loadDiscoveryRowsByMentorIds(candidateIds)
                : loadDiscoveryRowsInPageOrder(candidateIds);
        if (rows.isEmpty()) {
            return PageResponse.<MentorDiscoveryCardResponse>builder()
                    .content(List.of())
                    .page(requestedPage)
                    .size(requestedSize)
                    .totalElements(candidatePage.getTotalElements())
                    .totalPages(totalPages(candidatePage.getTotalElements(), requestedSize))
                    .last(isLastPage(requestedPage, requestedSize, candidatePage.getTotalElements()))
                    .build();
        }

        Map<UUID, List<MentorTagResponse>> helpTopicsByMentor = loadTagsByMentor(
                candidateIds,
                Set.of(MentorTagType.HELP_TOPIC)
        );

        List<MentorService> activeServices = relevanceSort
                ? loadActiveServicesByMentorIds(candidateIds)
                : List.of();
        Map<UUID, List<MentorService>> servicesByMentor = relevanceSort
                ? groupServicesByMentor(activeServices)
                : Map.of();

        List<MentorDiscoveryCardResponse> content;
        if (relevanceSort) {
            List<RankedSearchCandidate> rankedCandidates = rows.stream()
                    .map(row -> new RankedSearchCandidate(
                            row,
                            helpTopicsByMentor.getOrDefault(row.mentorUserId(), List.of()),
                            servicesByMentor.getOrDefault(row.mentorUserId(), List.of()),
                            calculateSearchScore(row, menteeProfile, normalizedKeyword, helpTopicsByMentor.getOrDefault(row.mentorUserId(), List.of()), servicesByMentor.getOrDefault(row.mentorUserId(), List.of()))
                    ))
                    .sorted(Comparator
                            .comparing(RankedSearchCandidate::score).reversed()
                            .thenComparing(candidate -> defaultDecimal(candidate.row().ratingAverage()), Comparator.reverseOrder())
                            .thenComparing(candidate -> defaultInteger(candidate.row().completedSessions()), Comparator.reverseOrder())
                            .thenComparing(candidate -> candidate.row().verifiedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(candidate -> candidate.row().mentorUserId()))
                    .toList();

            int fromIndex = Math.min(requestedPage * requestedSize, rankedCandidates.size());
            int toIndex = Math.min(fromIndex + requestedSize, rankedCandidates.size());
            content = rankedCandidates.subList(fromIndex, toIndex).stream()
                    .map(candidate -> toCardResponse(candidate.row(), candidate.helpTopics()))
                    .toList();
        } else {
            content = rows.stream()
                    .map(row -> toCardResponse(row, helpTopicsByMentor.getOrDefault(row.mentorUserId(), List.of())))
                    .toList();
        }

        return PageResponse.<MentorDiscoveryCardResponse>builder()
                .content(content)
                .page(requestedPage)
                .size(requestedSize)
                .totalElements(candidatePage.getTotalElements())
                .totalPages(totalPages(candidatePage.getTotalElements(), requestedSize))
                .last(isLastPage(requestedPage, requestedSize, candidatePage.getTotalElements()))
                .build();
    }

    @Transactional(readOnly = true)
    public List<MentorRecommendationResponse> getRecommendations(UUID currentUserId, int limit) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }

        int safeLimit = Math.min(Math.max(limit, 1), 12);
        StudentProfile menteeProfile = loadStudentProfileSafely(currentUserId);
        LocalDateTime now = currentTime();
        boolean richProfile = menteeProfile != null
                && menteeProfile.getProgram() != null
                && menteeProfile.getSpecialization() != null;
        int candidateFetchSize = richProfile ? Math.min(200, Math.max(safeLimit * 10, 60)) : Math.min(120, Math.max(safeLimit * 5, safeLimit));

        List<MentorDiscoveryQueryRow> candidates = mentorProfileRepository.findRecommendationCandidatesSortedByRelevance(
                MentorStatus.ACTIVE,
                MentorTagType.HELP_TOPIC,
                currentUserId,
                now,
                PageRequest.of(0, candidateFetchSize)
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
                .sorted(Comparator
                        .comparing(MentorRecommendationResponse::matchScore).reversed()
                        .thenComparing(response -> defaultInteger(response.mentor().completedSessions()), Comparator.reverseOrder())
                        .thenComparing(response -> defaultDecimal(response.mentor().ratingAverage()), Comparator.reverseOrder()))
                .limit(safeLimit)
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
        List<MentorServiceResponse> services = mentorServiceRepository
                .findByMentorProfileUserIdAndIsActiveTrueOrderByCreatedAtAsc(mentorUserId)
                .stream()
                .map(this::toServiceResponse)
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
                .bookingSuspendedUntil(mentorProfile.getBookingSuspendedUntil())
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

    @Transactional
    public List<MentorAvailabilitySlotResponse> getMentorAvailability(UUID mentorUserId, AvailabilityQueryRequest request) {
        MentorProfile mentorProfile = getDiscoverableMentorProfile(mentorUserId);
        if (isBookingSuspended(mentorProfile)) {
            return List.of();
        }
        AvailabilityQueryRequest safeRequest = request == null ? new AvailabilityQueryRequest() : request;
        return mentorAvailabilityService.getAvailableSlots(mentorProfile, safeRequest.getFromDate(), safeRequest.getToDate());
    }

    @Transactional(readOnly = true)
    public ServiceSlotCandidatesResponse getMentorAvailabilityCandidates(UUID mentorUserId, UUID slotId, UUID serviceId) {
        MentorProfile mentorProfile = getDiscoverableMentorProfile(mentorUserId);
        if (isBookingSuspended(mentorProfile)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor hiện đang tạm khóa nhận booking mới");
        }
        return mentorAvailabilityService.getServiceSlotCandidates(mentorUserId, slotId, serviceId);
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
        BigDecimal score = calculateMatchScore(candidate, menteeProfile, reasons);

        if (defaultInteger(candidate.completedSessions()) > 0 && reasons.stream().noneMatch("Đã có phiên mentoring hoàn thành"::equals)) {
            reasons.add("Đã có phiên mentoring hoàn thành");
        }

        if (reasons.isEmpty()) {
            reasons.add("Phù hợp với các tiêu chí discovery hiện tại");
        }

        return MentorRecommendationResponse.builder()
                .mentor(toCardResponse(candidate, helpTopicTags))
                .matchScore(score)
                .matchReasons(reasons.stream().limit(3).toList())
                .build();
    }

    private BigDecimal calculateMatchScore(MentorDiscoveryQueryRow candidate, StudentProfile menteeProfile, List<String> reasons) {
        BigDecimal baseScore = ZERO;

        if (menteeProfile != null) {
            if (sameUuid(menteeProfile.getProgram() == null ? null : menteeProfile.getProgram().getId(), candidate.programId())) {
                baseScore = baseScore.add(SAME_PROGRAM_SCORE);
                reasons.add("Cùng chương trình học");
            }
            if (sameUuid(menteeProfile.getSpecialization() == null ? null : menteeProfile.getSpecialization().getId(), candidate.specializationId())) {
                baseScore = baseScore.add(SAME_SPECIALIZATION_SCORE);
                reasons.add("Cùng chuyên ngành với mentee");
            }
            if (sameUuid(menteeProfile.getCampus() == null ? null : menteeProfile.getCampus().getId(), candidate.campusId())) {
                baseScore = baseScore.add(SAME_CAMPUS_SCORE);
                reasons.add("Cùng campus");
            }
            if (Boolean.TRUE.equals(candidate.alumni())) {
                baseScore = baseScore.add(MENTOR_ALUMNI_SCORE);
                reasons.add("Mentor là cựu sinh viên");
            } else {
                Integer menteeSemester = menteeProfile.getSemester();
                Integer mentorSemester = candidate.semester();
                if (menteeSemester != null && mentorSemester != null) {
                    if (mentorSemester > menteeSemester) {
                        baseScore = baseScore.add(MENTOR_HIGHER_SEMESTER_SCORE);
                        reasons.add("Mentor đi trước mentee về học kỳ");
                    } else if (mentorSemester.equals(menteeSemester)) {
                        baseScore = baseScore.add(MENTOR_EQUAL_SEMESTER_SCORE);
                        reasons.add("Mentor cùng học kỳ chuyên ngành với mentee");
                    }
                }
            }
        }

        BigDecimal rating = defaultDecimal(candidate.ratingAverage());
        int reviews = defaultInteger(candidate.reviewCount());
        int completedSessions = defaultInteger(candidate.completedSessions());
        if (rating.compareTo(BigDecimal.valueOf(4.5)) >= 0) {
            baseScore = baseScore.add(QUALITY_HIGH_RATING_BONUS);
        }
        if (reviews >= 5) {
            baseScore = baseScore.add(QUALITY_CREDIBLE_REVIEWS_BONUS);
        }
        if (completedSessions >= 10) {
            baseScore = baseScore.add(QUALITY_EXPERIENCED_SESSIONS_BONUS);
        }

        return baseScore.setScale(2, RoundingMode.HALF_UP);
    }

    private Map<UUID, List<MentorTagResponse>> loadTagsByMentor(Collection<UUID> mentorUserIds, Set<MentorTagType> tagTypes) {
        if (mentorUserIds == null || mentorUserIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<MentorTagResponse>> result = new HashMap<>();
        List<MentorTag> mentorTags = Optional.ofNullable(mentorTagRepository.findByIdMentorUserIdInAndIdTagTypeIn(mentorUserIds, tagTypes))
                .orElse(List.of());
        mentorTags
                .stream()
                .sorted(Comparator
                        .comparing((MentorTag mentorTag) -> mentorTag.getId().getMentorUserId())
                        .thenComparing(mentorTag -> mentorTag.getTag().getNameVi()))
                .forEach(mentorTag -> result.computeIfAbsent(mentorTag.getId().getMentorUserId(), ignored -> new ArrayList<>())
                        .add(toTagResponse(mentorTag)));
        return result;
    }

    private List<MentorService> loadActiveServicesByMentorIds(Collection<UUID> mentorUserIds) {
        if (mentorUserIds == null || mentorUserIds.isEmpty()) {
            return List.of();
        }
        return Optional.ofNullable(mentorServiceRepository.findByMentorProfileUserIdInAndIsActiveTrueOrderByCreatedAtAsc(new ArrayList<>(mentorUserIds)))
                .orElse(List.of());
    }

    private Map<UUID, List<MentorService>> groupServicesByMentor(List<MentorService> services) {
        if (services == null || services.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<MentorService>> grouped = new HashMap<>();
        for (MentorService service : services) {
            if (service == null || service.getMentorProfile() == null || service.getMentorProfile().getUserId() == null) {
                continue;
            }
            grouped.computeIfAbsent(service.getMentorProfile().getUserId(), ignored -> new ArrayList<>())
                    .add(service);
        }
        return grouped;
    }

    private String normalizeSearchText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        normalized = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }

    private List<String> tokenizeSearchText(String value) {
        String normalized = normalizeSearchText(value);
        if (normalized.isBlank()) {
            return List.of();
        }
        return Arrays.stream(normalized.split(" "))
                .filter(token -> token != null && !token.isBlank())
                .distinct()
                .toList();
    }

    private BigDecimal calculateSearchScore(
            MentorDiscoveryQueryRow row,
            StudentProfile menteeProfile,
            String normalizedKeyword,
            List<MentorTagResponse> helpTopics,
            List<MentorService> services
    ) {
        List<String> tokens = tokenizeSearchText(normalizedKeyword);
        if (tokens.isEmpty()) {
            return calculatePersonalizationScore(row, menteeProfile).add(calculateSearchQualityScore(row, menteeProfile));
        }

        BigDecimal score = ZERO;
        String exactKeyword = normalizeSearchText(normalizedKeyword);

        String normalizedHeadline = normalizeSearchText(row.headline());
        String normalizedSubjects = normalizeSearchText(row.supportingSubjects());
        if (!normalizedHeadline.isBlank() && normalizedHeadline.contains(exactKeyword)) {
            score = score.add(HEADLINE_EXACT_BONUS);
        }
        if (!normalizedSubjects.isBlank() && normalizedSubjects.contains(exactKeyword)) {
            score = score.add(SUBJECTS_PHRASE_BONUS);
        }

        List<String> profileFields = Arrays.stream(new String[]{
                        row.displayName(),
                        row.headline(),
                        row.expertiseDescription(),
                        row.supportingSubjects(),
                        row.bio(),
                        row.campusName(),
                        row.programName(),
                        row.specializationName()
                })
                .filter(value -> value != null && !value.isBlank())
                .toList();
        List<String> tagFields = helpTopics == null ? List.of() : helpTopics.stream()
                .flatMap(tag -> Arrays.stream(new String[]{tag.nameVi(), tag.nameEn(), tag.code()}))
                .filter(value -> value != null && !value.isBlank())
                .toList();
        List<String> serviceFields = services == null ? List.of() : services.stream()
                .flatMap(service -> Arrays.stream(new String[]{service.getTitle(), service.getDescription(), service.getExpectedOutcome()}))
                .filter(value -> value != null && !value.isBlank())
                .toList();

        if (containsPhrase(profileFields, exactKeyword) || containsPhrase(tagFields, exactKeyword) || containsPhrase(serviceFields, exactKeyword)) {
            score = score.add(decimal(50));
        }

        int profileMatches = countTokenMatches(profileFields, tokens);
        int tagMatches = countTokenMatches(tagFields, tokens);
        int serviceMatches = countTokenMatches(serviceFields, tokens);
        int totalMatches = profileMatches + tagMatches + serviceMatches;

        score = score.add(decimal(profileMatches * 8));
        score = score.add(decimal(tagMatches * 10));
        score = score.add(decimal(serviceMatches * 12));

        if (totalMatches > 0) {
            BigDecimal coverageBonus = BigDecimal.valueOf(totalMatches)
                    .multiply(BigDecimal.valueOf(10))
                    .divide(BigDecimal.valueOf(tokens.size()), 2, RoundingMode.HALF_UP);
            score = score.add(coverageBonus);
        }

        if (row.verifiedAt() != null) {
            LocalDateTime now = currentTime();
            if (row.verifiedAt().isAfter(now.minusDays(7))) {
                score = score.add(VERIFIED_RECENT_7D_BONUS);
            } else if (row.verifiedAt().isAfter(now.minusDays(30))) {
                score = score.add(VERIFIED_RECENT_30D_BONUS);
            }
        }

        int completedSessions = defaultInteger(row.completedSessions());
        if (completedSessions >= 50) {
            score = score.add(SESSION_VELOCITY_HIGH_BONUS);
        } else if (completedSessions >= 20) {
            score = score.add(SESSION_VELOCITY_MED_BONUS);
        }

        score = score.add(calculatePersonalizationScore(row, menteeProfile));
        score = score.add(calculateSearchQualityScore(row, menteeProfile));
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculatePersonalizationScore(MentorDiscoveryQueryRow candidate, StudentProfile menteeProfile) {
        BigDecimal baseScore = ZERO;
        if (menteeProfile == null) {
            return baseScore;
        }

        if (sameUuid(menteeProfile.getProgram() == null ? null : menteeProfile.getProgram().getId(), candidate.programId())) {
            baseScore = baseScore.add(SAME_PROGRAM_SCORE);
        }
        if (sameUuid(menteeProfile.getSpecialization() == null ? null : menteeProfile.getSpecialization().getId(), candidate.specializationId())) {
            baseScore = baseScore.add(SAME_SPECIALIZATION_SCORE);
        }
        if (sameUuid(menteeProfile.getCampus() == null ? null : menteeProfile.getCampus().getId(), candidate.campusId())) {
            baseScore = baseScore.add(SAME_CAMPUS_SCORE);
        }
        if (Boolean.TRUE.equals(candidate.alumni())) {
            baseScore = baseScore.add(MENTOR_ALUMNI_SCORE);
        } else {
            Integer menteeSemester = menteeProfile.getSemester();
            Integer mentorSemester = candidate.semester();
            if (menteeSemester != null && mentorSemester != null) {
                if (mentorSemester > menteeSemester) {
                    baseScore = baseScore.add(MENTOR_HIGHER_SEMESTER_SCORE);
                } else if (mentorSemester.equals(menteeSemester)) {
                    baseScore = baseScore.add(MENTOR_EQUAL_SEMESTER_SCORE);
                }
            }
        }

        return baseScore;
    }

    private BigDecimal calculateSearchQualityScore(MentorDiscoveryQueryRow row, StudentProfile menteeProfile) {
        BigDecimal score = ZERO;
        BigDecimal rating = defaultDecimal(row.ratingAverage());
        int reviews = defaultInteger(row.reviewCount());
        int completedSessions = defaultInteger(row.completedSessions());

        score = score.add(rating.multiply(BigDecimal.valueOf(2)));
        score = score.add(BigDecimal.valueOf(Math.min(reviews, 10)));
        score = score.add(BigDecimal.valueOf(Math.min(completedSessions, 100) / 10.0).setScale(2, RoundingMode.HALF_UP));

        if (menteeProfile != null && Boolean.TRUE.equals(row.alumni())) {
            score = score.add(decimal(5));
        }
        return score;
    }

    private int countTokenMatches(List<String> fields, List<String> tokens) {
        if (fields == null || fields.isEmpty() || tokens == null || tokens.isEmpty()) {
            return 0;
        }
        Set<String> matchedTokens = new LinkedHashSet<>();
        for (String token : tokens) {
            for (String field : fields) {
                if (containsToken(field, token)) {
                    matchedTokens.add(token);
                    break;
                }
            }
        }
        return matchedTokens.size();
    }

    private boolean containsPhrase(List<String> fields, String normalizedPhrase) {
        if (fields == null || fields.isEmpty() || normalizedPhrase == null || normalizedPhrase.isBlank()) {
            return false;
        }
        return fields.stream().filter(value -> value != null && !value.isBlank())
                .map(this::normalizeSearchText)
                .anyMatch(normalized -> normalized.contains(normalizedPhrase));
    }

    private boolean containsToken(String field, String token) {
        if (field == null || field.isBlank() || token == null || token.isBlank()) {
            return false;
        }
        return normalizeSearchText(field).contains(token);
    }

    private StudentProfile loadStudentProfileSafely(UUID userId) {
        Optional<StudentProfile> studentProfile = studentProfileRepository.findWithDetailsByUserId(userId);
        return studentProfile == null ? null : studentProfile.orElse(null);
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

    private MentorServiceResponse toServiceResponse(MentorService mentorService) {
        List<MentorTagResponse> helpTopics = mentorService.getHelpTopics().stream()
                .sorted(Comparator.comparing(tag -> tag.getNameVi() == null ? "" : tag.getNameVi()))
                .map(tag -> MentorTagResponse.builder()
                        .id(tag.getId())
                        .code(tag.getCode())
                        .nameVi(tag.getNameVi())
                        .nameEn(tag.getNameEn())
                        .type(tag.getType())
                        .primary(false)
                        .build())
                .toList();

        return MentorServiceResponse.builder()
                .serviceId(mentorService.getId())
                .mentorUserId(mentorService.getMentorProfile() == null ? null : mentorService.getMentorProfile().getUserId())
                .title(mentorService.getTitle())
                .description(mentorService.getDescription())
                .durationMinutes(mentorService.getDurationMinutes())
                .free(mentorService.isFree())
                .priceAmount(defaultDecimal(mentorService.getPriceAmount()))
                .currency(mentorService.getCurrency())
                .active(mentorService.isActive())
                .helpTopics(helpTopics)
                .createdAt(mentorService.getCreatedAt())
                .updatedAt(mentorService.getUpdatedAt())
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
                && mentorProfile.getUser() != null
                && mentorProfile.getUser().getStatus() == com.fptu.exe.skillswap.modules.identity.domain.UserStatus.ACTIVE
                && mentorProfile.getUser().getRoles().contains(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTOR)
                && !mentorProfile.getUser().getRoles().contains(com.fptu.exe.skillswap.shared.constant.RoleCode.ADMIN)
                && !mentorProfile.getUser().getRoles().contains(com.fptu.exe.skillswap.shared.constant.RoleCode.SYSTEM_ADMIN)
                && mentorProfile.getVerifiedAt() != null
                && hasText(mentorProfile.getHeadline())
                && hasText(mentorProfile.getExpertiseDescription())
                && mentorProfile.isAvailable()
                && mentorProfile.getTeachingMode() != null
                && mentorProfile.getSessionDuration() != null
                && !isBookingSuspended(mentorProfile);
    }

    private boolean isBookingSuspended(MentorProfile mentorProfile) {
        return mentorProfile != null
                && mentorProfile.getBookingSuspendedUntil() != null
                && mentorProfile.getBookingSuspendedUntil().isAfter(currentTime());
    }

    private LocalDateTime currentTime() {
        return LocalDateTime.now(APP_ZONE);
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

    private List<MentorDiscoveryQueryRow> loadDiscoveryRowsByMentorIds(List<UUID> mentorUserIds) {
        if (mentorUserIds == null || mentorUserIds.isEmpty()) {
            return List.of();
        }
        return mentorProfileRepository.findDiscoveryRowsByMentorUserIds(mentorUserIds);
    }

    private List<MentorDiscoveryQueryRow> loadDiscoveryRowsInPageOrder(List<UUID> mentorUserIds) {
        if (mentorUserIds == null || mentorUserIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, Integer> orderByMentorId = new LinkedHashMap<>();
        for (int index = 0; index < mentorUserIds.size(); index++) {
            orderByMentorId.putIfAbsent(mentorUserIds.get(index), index);
        }

        return mentorProfileRepository.findDiscoveryRowsByMentorUserIds(mentorUserIds).stream()
                .sorted(Comparator.comparingInt(row -> orderByMentorId.getOrDefault(row.mentorUserId(), Integer.MAX_VALUE)))
                .toList();
    }

    private int relevanceWindowSize(int requestedPage, int requestedSize) {
        int minWindow = Math.max(requestedSize * 5, requestedSize);
        int requestedWindow = Math.max((requestedPage + 1) * requestedSize * 5, minWindow);
        return Math.min(200, requestedWindow);
    }

    private int totalPages(long totalElements, int pageSize) {
        if (pageSize <= 0 || totalElements <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalElements / pageSize);
    }

    private boolean isLastPage(int page, int size, long totalElements) {
        int totalPages = totalPages(totalElements, size);
        return totalPages == 0 || page + 1 >= totalPages;
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

    private boolean isPostgresDataSource() {
        if (postgresDetected != null) {
            return postgresDetected;
        }
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String productName = meta.getDatabaseProductName();
            postgresDetected = productName != null && productName.toLowerCase(Locale.ROOT).contains("postgresql");
        } catch (Exception ex) {
            postgresDetected = false;
        }
        return postgresDetected;
    }

    private org.springframework.data.domain.Page<UUID> findCandidatesByFts(
            String normalizedKeyword,
            MentorDiscoverySearchRequest safeRequest,
            List<UUID> tagIds,
            int requestedPage,
            int requestedSize,
            boolean relevanceSort
    ) {
        String teachingModeStr = safeRequest.getTeachingMode() == null ? null : safeRequest.getTeachingMode().name();
        boolean hasTagFilter = hasTagFilter(safeRequest.getTagIds());
        String tagIdsArray = tagIds.isEmpty()
                ? "{00000000-0000-0000-0000-000000000000}"
                : "{" + tagIds.stream().map(UUID::toString).collect(Collectors.joining(",")) + "}";
        LocalDateTime now = currentTime();

        int fetchLimit = relevanceSort ? Math.min(200, (requestedPage + 1) * requestedSize * 5 + requestedSize) : requestedSize;
        int fetchOffset = relevanceSort ? 0 : requestedPage * requestedSize;

        List<UUID> ids = mentorProfileRepository.findDiscoverableCandidateIdsByFts(
                normalizedKeyword,
                safeRequest.getCampusId(),
                safeRequest.getSpecializationId(),
                teachingModeStr,
                hasTagFilter,
                tagIdsArray,
                now,
                fetchLimit,
                fetchOffset
        );

        long totalCount = mentorProfileRepository.countDiscoverableCandidatesByFts(
                normalizedKeyword,
                safeRequest.getCampusId(),
                safeRequest.getSpecializationId(),
                teachingModeStr,
                hasTagFilter,
                tagIdsArray,
                now
        );

        return new org.springframework.data.domain.PageImpl<>(
                ids,
                org.springframework.data.domain.PageRequest.of(relevanceSort ? 0 : requestedPage, relevanceSort ? Math.max(fetchLimit, 1) : requestedSize),
                totalCount
        );
    }

    private String toLikePattern(String value) {
        String normalized = normalizeSearchText(value);
        if (normalized.isBlank()) {
            return null;
        }
        return "%" + normalized + "%";
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

    private record RankedSearchCandidate(
            MentorDiscoveryQueryRow row,
            List<MentorTagResponse> helpTopics,
            List<MentorService> services,
            BigDecimal score
    ) {
    }
}
