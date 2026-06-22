package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.dto.request.AdminMentorListRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.AdminMentorListItemResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.AdminMentorDetailResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminMentorService {

    private static final String ACCENTED_CHARACTERS = "àáạảãăắằẳẵặâấầẩẫậđèéẹẻẽêếềểễệìíịỉĩòóọỏõôốồổỗộơớờởỡợùúụủũưứừửữựỳýỵỷỹ";
    private static final String PLAIN_CHARACTERS = "aaaaaaaaaaaaaaaaadeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyy";

    private final MentorProfileRepository mentorProfileRepository;
    private final StudentProfileRepository studentProfileRepository;

    @Transactional(readOnly = true)
    public PageResponse<AdminMentorListItemResponse> getMentors(AdminMentorListRequest request) {
        AdminMentorListRequest safeRequest = request == null ? new AdminMentorListRequest() : request;
        Page<AdminMentorListItemResponse> page = mentorProfileRepository.searchForAdmin(
                buildKeywordPattern(safeRequest.getKeyword()),
                buildNormalizedKeywordPattern(safeRequest.getKeyword()),
                ACCENTED_CHARACTERS,
                PLAIN_CHARACTERS,
                safeRequest.getStatus(),
                safeRequest.getIsAvailable(),
                adminMentorPageable(safeRequest)
        );

        return PageResponse.<AdminMentorListItemResponse>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public AdminMentorDetailResponse getMentorDetail(UUID mentorUserId) {
        MentorProfile profile = mentorProfileRepository.findWithUserByUserId(mentorUserId)
                .orElseThrow(() -> new com.fptu.exe.skillswap.shared.exception.BaseException(
                        com.fptu.exe.skillswap.shared.exception.ErrorCode.NOT_FOUND,
                        "Không tìm thấy thông tin mentor"
                ));

        User user = profile.getUser();
        String primaryLabel = studentProfileRepository.findWithDetailsByUserId(mentorUserId)
                .map(sp -> sp.getProgram() == null ? null : sp.getProgram().getCode())
                .orElse(null);

        return AdminMentorDetailResponse.builder()
                .mentorUserId(profile.getUserId())
                .email(user == null ? null : user.getEmail())
                .displayName(user == null ? null : user.getFullName())
                .avatarUrl(user == null ? null : user.getAvatarUrl())
                .phoneNumber(profile.getPhoneNumber())
                .userStatus(user == null ? null : user.getStatus())
                .mentorStatus(profile.getStatus())
                .isAvailable(profile.isAvailable())
                .bookingSuspendedUntil(profile.getBookingSuspendedUntil())
                .headline(profile.getHeadline())
                .expertiseDescription(profile.getExpertiseDescription())
                .supportingSubjects(profile.getSupportingSubjects())
                .teachingMode(profile.getTeachingMode())
                .sessionDuration(profile.getSessionDuration())
                .ratingAverage(profile.getAverageRating())
                .reviewCount(profile.getTotalReviews())
                .completedSessions(profile.getTotalCompletedSessions())
                .rejectedBookings(profile.getTotalRejectedBookings())
                .lateCancellationPenaltyPoints(profile.getLateCancellationPenaltyPoints())
                .portfolioUrl(profile.getPortfolioUrl())
                .linkedinUrl(profile.getLinkedinUrl())
                .githubUrl(profile.getGithubUrl())
                .primaryLabel(primaryLabel)
                .verifiedAt(profile.getVerifiedAt())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    private Pageable adminMentorPageable(AdminMentorListRequest request) {
        int page = Math.max(request.getPage(), 0);
        int size = Math.min(Math.max(request.getSize(), 1), 100);
        Sort.Direction direction = request.resolveDirection();
        String sortBy = switch (request.getSortBy() == null ? "" : request.getSortBy()) {
            case "verifiedAt" -> "verifiedAt";
            case "averageRating" -> "averageRating";
            case "totalReviews" -> "totalReviews";
            case "totalCompletedSessions", "completedSessions" -> "totalCompletedSessions";
            case "status" -> "status";
            case "createdAt" -> "createdAt";
            default -> "updatedAt";
        };
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private String buildKeywordPattern(String keyword) {
        String normalized = normalizeKeyword(keyword);
        if (normalized == null) {
            return null;
        }
        return "%" + normalized.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ") + "%";
    }

    private String buildNormalizedKeywordPattern(String keyword) {
        String normalized = normalizeSearchText(keyword);
        if (normalized == null) {
            return null;
        }
        return "%" + normalized + "%";
    }

    private String normalizeSearchText(String keyword) {
        String normalized = normalizeKeyword(keyword);
        if (normalized == null) {
            return null;
        }
        return Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
