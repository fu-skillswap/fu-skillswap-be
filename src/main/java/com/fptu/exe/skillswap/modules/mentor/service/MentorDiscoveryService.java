package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTag;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagType;
import com.fptu.exe.skillswap.modules.catalog.repository.MentorTagRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorDiscoveryCardResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorDiscoverySearchRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorRecommendationResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorTagResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
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
    private static final BigDecimal AVAILABILITY_SCORE = decimal(12);
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

    @Transactional(readOnly = true)
    public PageResponse<MentorDiscoveryCardResponse> searchMentors(UUID currentUserId, MentorDiscoverySearchRequest request) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }

        MentorDiscoverySearchRequest safeRequest = request == null ? new MentorDiscoverySearchRequest() : request;
        List<UUID> tagIds = normalizedTagIds(safeRequest.getTagIds());

        Page<MentorDiscoveryQueryRow> page = mentorProfileRepository.searchDiscoverableMentors(
                MentorStatus.ACTIVE,
                MentorTagType.EXPERTISE,
                MentorTagType.HELP_TOPIC,
                normalizeKeyword(safeRequest.getKeyword()),
                safeRequest.getCampusId(),
                safeRequest.getSpecializationId(),
                safeRequest.getTeachingMode(),
                safeRequest.getIsAvailable(),
                hasTagFilter(safeRequest.getTagIds()),
                tagIds,
                searchPageable(safeRequest)
        );

        StudentProfile menteeProfile = studentProfileRepository.findWithDetailsByUserId(currentUserId).orElse(null);
        Map<UUID, List<MentorTagResponse>> expertiseTagsByMentor = loadTagsByMentor(
                page.getContent().stream().map(MentorDiscoveryQueryRow::mentorUserId).toList(),
                Set.of(MentorTagType.EXPERTISE)
        );

        List<MentorDiscoveryCardResponse> rankedContent = page.getContent().stream()
                .map(row -> new RankedMentorCard(
                        toCardResponse(row, expertiseTagsByMentor.getOrDefault(row.mentorUserId(), List.of())),
                        calculateMatchScore(row, menteeProfile, new ArrayList<>())
                ))
                .sorted(Comparator
                        .comparing(RankedMentorCard::matchScore, Comparator.reverseOrder())
                        .thenComparing(ranked -> {
                            Integer completed = ranked.card().completedSessions();
                            return completed == null ? 0 : completed;
                        }, Comparator.reverseOrder())
                        .thenComparing(ranked -> defaultDecimal(ranked.card().ratingAverage()), Comparator.reverseOrder()))
                .map(RankedMentorCard::card)
                .toList();

        return PageResponse.<MentorDiscoveryCardResponse>builder()
                .content(rankedContent)
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
        List<MentorDiscoveryQueryRow> candidates = mentorProfileRepository.findRecommendationCandidates(
                MentorStatus.ACTIVE,
                MentorTagType.EXPERTISE,
                MentorTagType.HELP_TOPIC,
                currentUserId,
                PageRequest.of(0, Math.max(24, safeLimit * 6), Sort.by(
                        Sort.Order.desc("isAvailable"),
                        Sort.Order.desc("averageRating"),
                        Sort.Order.desc("totalCompletedSessions"),
                        Sort.Order.desc("verifiedAt")
                ))
        );

        if (candidates.isEmpty()) {
            return List.of();
        }

        StudentProfile menteeProfile = studentProfileRepository.findWithDetailsByUserId(currentUserId).orElse(null);
        Map<UUID, List<MentorTagResponse>> expertiseTagsByMentor = loadTagsByMentor(
                candidates.stream().map(MentorDiscoveryQueryRow::mentorUserId).toList(),
                Set.of(MentorTagType.EXPERTISE)
        );

        return candidates.stream()
                .map(candidate -> toRecommendation(candidate, expertiseTagsByMentor.getOrDefault(candidate.mentorUserId(), List.of()), menteeProfile))
                .sorted(Comparator
                        .comparing(MentorRecommendationResponse::matchScore, Comparator.reverseOrder())
                        .thenComparing(response -> {
                            Integer completed = response.mentor().completedSessions();
                            return completed == null ? 0 : completed;
                        }, Comparator.reverseOrder()))
                .limit(safeLimit)
                .toList();
    }

    private MentorRecommendationResponse toRecommendation(
            MentorDiscoveryQueryRow candidate,
            List<MentorTagResponse> expertiseTags,
            StudentProfile menteeProfile
    ) {
        List<String> reasons = new ArrayList<>();
        BigDecimal score = calculateMatchScore(candidate, menteeProfile, reasons);

        if (defaultInteger(candidate.completedSessions()) > 0 && reasons.stream().noneMatch("Đã có nhiều phiên mentoring hoàn thành"::equals)) {
            reasons.add("Đã có phiên mentoring hoàn thành");
        }

        if (reasons.isEmpty()) {
            reasons.add("Phù hợp với các tiêu chí discovery hiện tại");
        }

        return MentorRecommendationResponse.builder()
                .mentor(toCardResponse(candidate, expertiseTags))
                .matchScore(score.setScale(2, RoundingMode.HALF_UP))
                .matchReasons(reasons.stream().limit(3).toList())
                .build();
    }

    private BigDecimal calculateMatchScore(MentorDiscoveryQueryRow candidate, StudentProfile menteeProfile, List<String> reasons) {
        BigDecimal baseScore = ZERO;

        if (candidate.isAvailable() != null && candidate.isAvailable()) {
            baseScore = baseScore.add(AVAILABILITY_SCORE);
            reasons.add("Mentor đang mở nhận mentee");
        }

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
        if (normalizedRating.compareTo(BigDecimal.ZERO) <= 0 || normalizedReviewCount <= 0) {
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

    private MentorDiscoveryCardResponse toCardResponse(MentorDiscoveryQueryRow row, List<MentorTagResponse> expertiseTags) {
        return MentorDiscoveryCardResponse.builder()
                .mentorUserId(row.mentorUserId())
                .displayName(row.displayName())
                .avatarUrl(row.avatarUrl())
                .headline(row.headline())
                .currentPosition(row.currentPosition())
                .currentCompany(row.currentCompany())
                .isAvailable(row.isAvailable())
                .ratingAverage(defaultDecimal(row.ratingAverage()))
                .reviewCount(defaultInteger(row.reviewCount()))
                .completedSessions(defaultInteger(row.completedSessions()))
                .hourlyRate(defaultDecimal(row.hourlyRate()))
                .teachingMode(row.teachingMode())
                .verifiedAt(row.verifiedAt())
                .campusId(row.campusId())
                .campusName(row.campusName())
                .programId(row.programId())
                .programName(row.programName())
                .specializationId(row.specializationId())
                .specializationName(row.specializationName())
                .expertiseTags(expertiseTags.stream().limit(5).toList())
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
            case "hourlyRate" -> List.of(
                    new Sort.Order(direction, "hourlyRate"),
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

    private boolean sameUuid(UUID left, UUID right) {
        return left != null && left.equals(right);
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
