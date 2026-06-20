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
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorTagResponse;
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
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MentorDiscoveryService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final UUID EMPTY_TAG_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal SAME_PROGRAM_SCORE = decimal(40);
    private static final BigDecimal SAME_SPECIALIZATION_SCORE = decimal(30);
    private static final BigDecimal SAME_CAMPUS_SCORE = decimal(10);
    private static final BigDecimal MENTOR_ALUMNI_SCORE = decimal(20);
    private static final BigDecimal MENTOR_HIGHER_SEMESTER_SCORE = decimal(15);
    private static final BigDecimal MENTOR_EQUAL_SEMESTER_SCORE = decimal(10);
    private static final BigDecimal SEARCH_EXACT_PHRASE_SCORE = decimal(50);
    private static final BigDecimal SEARCH_TOKEN_SCORE = decimal(8);
    private static final BigDecimal SEARCH_TAG_SCORE = decimal(10);
    private static final BigDecimal SEARCH_SERVICE_SCORE = decimal(12);
    private static final BigDecimal SEARCH_BIO_SCORE = decimal(8);
    private static final int SEARCH_CANDIDATE_CAP = 200;

    private final MentorProfileRepository mentorProfileRepository;
    private final MentorTagRepository mentorTagRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final MentorServiceRepository mentorServiceRepository;
    private final MentorAvailabilityService mentorAvailabilityService;
    private final SessionFeedbackRepository sessionFeedbackRepository;

    @Transactional(readOnly = true)
    public PageResponse<MentorDiscoveryCardResponse> searchMentors(UUID currentUserId, MentorDiscoverySearchRequest request) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }

        MentorDiscoverySearchRequest safeRequest = request == null ? new MentorDiscoverySearchRequest() : request;
        List<UUID> tagIds = normalizedTagIds(safeRequest.getTagIds());
        boolean hasKeyword = safeRequest.getKeyword() != null && !safeRequest.getKeyword().isBlank();
        String normalizedKeyword = normalizeKeyword(safeRequest.getKeyword());
        List<String> keywordTokens = normalizeTokens(normalizedKeyword);

        StudentProfile menteeProfile = studentProfileRepository.findWithDetailsByUserId(currentUserId).orElse(null);
        int requestedPage = Math.max(safeRequest.getPage(), 0);
        int requestedSize = Math.min(Math.max(safeRequest.getSize(), 1), 30);
        boolean relevanceSort = isRelevanceSort(safeRequest.getSortBy());
        int fetchSize = Math.min(
                SEARCH_CANDIDATE_CAP,
                Math.max(requestedSize * Math.max(requestedPage + 1, 1) * 5, requestedSize * 5)
        );

        List<UUID> candidateIds = mentorProfileRepository.findDiscoverableCandidateIds(
                MentorStatus.ACTIVE,
                MentorTagType.HELP_TOPIC,
                safeRequest.getCampusId(),
                safeRequest.getSpecializationId(),
                safeRequest.getTeachingMode(),
                hasTagFilter(safeRequest.getTagIds()),
                tagIds,
                currentTime(),
                PageRequest.of(0, fetchSize)
        );

        List<MentorDiscoveryQueryRow> rows = loadDiscoveryRowsInPageOrder(candidateIds);
        if (rows.isEmpty()) {
            return PageResponse.<MentorDiscoveryCardResponse>builder()
                    .content(List.of())
                    .page(requestedPage)
                    .size(requestedSize)
                    .totalElements(0)
                    .totalPages(0)
                    .last(true)
                    .build();
        }

        Map<UUID, List<MentorTagResponse>> helpTopicsByMentor = loadTagsByMentor(
                rows.stream().map(MentorDiscoveryQueryRow::mentorUserId).toList(),
                Set.of(MentorTagType.HELP_TOPIC)
        );
        Map<UUID, List<MentorServiceResponse>> servicesByMentor = loadServicesByMentor(
                rows.stream().map(MentorDiscoveryQueryRow::mentorUserId).toList()
        );

        List<SearchRankedMentor> rankedMentors = rows.stream()
                .map(row -> rankSearchCandidate(
                        row,
                        helpTopicsByMentor.getOrDefault(row.mentorUserId(), List.of()),
                        servicesByMentor.getOrDefault(row.mentorUserId(), List.of()),
                        menteeProfile,
                        normalizedKeyword,
                        keywordTokens,
                        hasKeyword || relevanceSort,
                        hasKeyword
                ))
                .toList();

        if (hasKeyword) {
            rankedMentors = rankedMentors.stream()
                    .sorted(Comparator
                            .comparing(SearchRankedMentor::keywordScore).reversed()
                            .thenComparing(SearchRankedMentor::profileScore, Comparator.reverseOrder())
                            .thenComparing(candidate -> defaultDecimal(candidate.row().ratingAverage()), Comparator.reverseOrder())
                            .thenComparing(candidate -> defaultInteger(candidate.row().completedSessions()), Comparator.reverseOrder())
                            .thenComparing(candidate -> candidate.row().verifiedAt(), Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        } else if (relevanceSort) {
            rankedMentors = rankedMentors.stream()
                    .sorted(Comparator
                            .comparing(SearchRankedMentor::profileScore).reversed()
                            .thenComparing(candidate -> defaultDecimal(candidate.row().ratingAverage()), Comparator.reverseOrder())
                            .thenComparing(candidate -> defaultInteger(candidate.row().completedSessions()), Comparator.reverseOrder())
                    .thenComparing(candidate -> candidate.row().verifiedAt(), Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        } else {
            rankedMentors = rankedMentors.stream()
                    .sorted(searchComparator(safeRequest))
                    .toList();
        }

        int fromIndex = Math.min(requestedPage * requestedSize, rankedMentors.size());
        int toIndex = Math.min(fromIndex + requestedSize, rankedMentors.size());
        List<MentorDiscoveryCardResponse> content = rankedMentors.subList(fromIndex, toIndex).stream()
                .map(candidate -> toCardResponse(candidate.row(), helpTopicsByMentor.getOrDefault(candidate.row().mentorUserId(), List.of())))
                .toList();

        long totalElements = rankedMentors.size();
        int totalPages = requestedSize == 0 ? 0 : (int) Math.ceil((double) totalElements / requestedSize);

        return PageResponse.<MentorDiscoveryCardResponse>builder()
                .content(content)
                .page(requestedPage)
                .size(requestedSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .last(requestedPage >= Math.max(totalPages - 1, 0))
                .build();
    }

    @Transactional(readOnly = true)
    public List<MentorRecommendationResponse> getRecommendations(UUID currentUserId, int limit) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }

        int safeLimit = Math.min(Math.max(limit, 1), 12);
        StudentProfile menteeProfile = studentProfileRepository.findWithDetailsByUserId(currentUserId).orElse(null);
        LocalDateTime now = currentTime();
        int candidateFetchSize = Math.min(30, Math.max(safeLimit * 3, safeLimit));

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

        return baseScore.setScale(2, RoundingMode.HALF_UP);
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

    private Map<UUID, List<MentorServiceResponse>> loadServicesByMentor(Collection<UUID> mentorUserIds) {
        if (mentorUserIds == null || mentorUserIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<MentorServiceResponse>> result = new HashMap<>();
        mentorServiceRepository.findByMentorProfileUserIdInAndIsActiveTrueOrderByCreatedAtAsc(new ArrayList<>(mentorUserIds))
                .forEach(service -> result.computeIfAbsent(
                                service.getMentorProfile() == null ? null : service.getMentorProfile().getUserId(),
                                ignored -> new ArrayList<>())
                        .add(toServiceResponse(service)));
        result.remove(null);
        return result;
    }

    private SearchRankedMentor rankSearchCandidate(
            MentorDiscoveryQueryRow row,
            List<MentorTagResponse> helpTopicTags,
            List<MentorServiceResponse> services,
            StudentProfile menteeProfile,
            String normalizedKeyword,
            List<String> keywordTokens,
            boolean relevanceSort,
            boolean keywordSearch
    ) {
        BigDecimal keywordScore = keywordSearch
                ? calculateKeywordScore(row, helpTopicTags, services, normalizedKeyword, keywordTokens)
                : ZERO;
        BigDecimal profileScore = relevanceSort ? calculateMatchScore(row, menteeProfile, new ArrayList<>()) : ZERO;
        return new SearchRankedMentor(row, helpTopicTags, services, keywordScore, profileScore);
    }

    private BigDecimal calculateKeywordScore(
            MentorDiscoveryQueryRow row,
            List<MentorTagResponse> helpTopicTags,
            List<MentorServiceResponse> services,
            String normalizedKeyword,
            List<String> keywordTokens
    ) {
        if (normalizedKeyword == null || normalizedKeyword.isBlank()) {
            return ZERO;
        }

        String fullText = normalizeSearchText(buildSearchCorpus(row, helpTopicTags, services));
        if (fullText.isBlank()) {
            return ZERO;
        }

        BigDecimal score = ZERO;
        if (fullText.contains(normalizedKeyword)) {
            score = score.add(SEARCH_EXACT_PHRASE_SCORE);
        }

        Set<String> normalizedTokenSet = keywordTokens == null ? Set.of() : new LinkedHashSet<>(keywordTokens);
        int matchedTokens = 0;
        for (String token : normalizedTokenSet) {
            if (token.isBlank()) {
                continue;
            }
            if (containsToken(fullText, token)) {
                matchedTokens++;
                score = score.add(SEARCH_TOKEN_SCORE);
            }
        }

        for (MentorTagResponse tag : helpTopicTags) {
            String tagText = normalizeSearchText((tag.nameVi() == null ? "" : tag.nameVi()) + " " + (tag.code() == null ? "" : tag.code()));
            if (!tagText.isBlank() && containsAnyToken(tagText, normalizedTokenSet)) {
                score = score.add(SEARCH_TAG_SCORE);
            }
        }

        for (MentorServiceResponse service : services) {
            String serviceText = normalizeSearchText((service.title() == null ? "" : service.title()) + " " + (service.description() == null ? "" : service.description()));
            if (!serviceText.isBlank() && containsAnyToken(serviceText, normalizedTokenSet)) {
                score = score.add(SEARCH_SERVICE_SCORE);
            }
        }

        if (row.bio() != null && containsAnyToken(normalizeSearchText(row.bio()), normalizedTokenSet)) {
            score = score.add(SEARCH_BIO_SCORE);
        }

        if (matchedTokens > 0 && !normalizedTokenSet.isEmpty()) {
            BigDecimal coverageBonus = BigDecimal.valueOf((matchedTokens * 100L) / normalizedTokenSet.size())
                    .divide(BigDecimal.valueOf(10), 2, RoundingMode.HALF_UP);
            score = score.add(coverageBonus);
        }

        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean containsAnyToken(String text, Set<String> tokens) {
        if (text == null || text.isBlank() || tokens == null || tokens.isEmpty()) {
            return false;
        }
        for (String token : tokens) {
            if (containsToken(text, token)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsToken(String text, String token) {
        if (text == null || token == null || token.isBlank()) {
            return false;
        }
        return text.contains(token) || text.startsWith(token);
    }

    private String buildSearchCorpus(MentorDiscoveryQueryRow row, List<MentorTagResponse> helpTopicTags, List<MentorServiceResponse> services) {
        List<String> parts = new ArrayList<>();
        addIfHasText(parts, row.displayName());
        addIfHasText(parts, row.headline());
        addIfHasText(parts, row.expertiseDescription());
        addIfHasText(parts, row.supportingSubjects());
        addIfHasText(parts, row.bio());
        addIfHasText(parts, row.campusName());
        addIfHasText(parts, row.programName());
        addIfHasText(parts, row.specializationName());
        helpTopicTags.forEach(tag -> {
            addIfHasText(parts, tag.nameVi());
            addIfHasText(parts, tag.nameEn());
            addIfHasText(parts, tag.code());
        });
        services.forEach(service -> {
            addIfHasText(parts, service.title());
            addIfHasText(parts, service.description());
            if (service.helpTopics() != null) {
                service.helpTopics().forEach(tag -> {
                    addIfHasText(parts, tag.nameVi());
                    addIfHasText(parts, tag.code());
                });
            }
        });
        return String.join(" ", parts);
    }

    private void addIfHasText(List<String> parts, String value) {
        if (value != null && !value.trim().isEmpty()) {
            parts.add(value);
        }
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

    private List<String> normalizeTokens(String keyword) {
        String normalizedKeyword = normalizeSearchText(keyword);
        if (normalizedKeyword.isBlank()) {
            return List.of();
        }

        Set<String> tokens = new LinkedHashSet<>();
        for (String token : normalizedKeyword.split("\\s+")) {
            if (!token.isBlank()) {
                tokens.add(token);
                tokens.addAll(expandSearchToken(token));
            }
        }
        return tokens.stream().filter(token -> !token.isBlank()).toList();
    }

    private List<String> expandSearchToken(String token) {
        return switch (token) {
            case "cv", "resume" -> List.of("cv", "resume", "ho so");
            case "interview" -> List.of("phong van");
            case "project" -> List.of("do an");
            case "study", "planning", "plan" -> List.of("ke hoach hoc tap");
            default -> List.of();
        };
    }

    private record SearchRankedMentor(
            MentorDiscoveryQueryRow row,
            List<MentorTagResponse> helpTopics,
            List<MentorServiceResponse> services,
            BigDecimal keywordScore,
            BigDecimal profileScore
    ) {
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
                && mentorProfile.getVerifiedAt() != null
                && hasText(mentorProfile.getHeadline())
                && hasText(mentorProfile.getExpertiseDescription())
                && mentorProfile.isAvailable()
                && mentorProfile.getTeachingMode() != null
                && mentorProfile.getSessionDuration() != null;
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

    private Comparator<SearchRankedMentor> searchComparator(MentorDiscoverySearchRequest request) {
        boolean ascending = request.getDirection() == Sort.Direction.ASC;
        String sortBy = request.getSortBy() == null ? "relevance" : request.getSortBy().trim();

        Comparator<SearchRankedMentor> primary = switch (sortBy) {
            case "ratingAverage" -> Comparator.comparing(candidate -> defaultDecimal(candidate.row().ratingAverage()));
            case "reviewCount" -> Comparator.comparing(candidate -> defaultInteger(candidate.row().reviewCount()));
            case "completedSessions" -> Comparator.comparing(candidate -> defaultInteger(candidate.row().completedSessions()));
            case "updatedAt" -> Comparator.comparing(
                    candidate -> candidate.row().verifiedAt(),
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            default -> Comparator.comparing(SearchRankedMentor::profileScore);
        };
        if (!ascending) {
            primary = primary.reversed();
        }
        return primary
                .thenComparing(candidate -> defaultDecimal(candidate.row().ratingAverage()), Comparator.reverseOrder())
                .thenComparing(candidate -> defaultInteger(candidate.row().completedSessions()), Comparator.reverseOrder())
                .thenComparing(candidate -> candidate.row().mentorUserId());
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
}
