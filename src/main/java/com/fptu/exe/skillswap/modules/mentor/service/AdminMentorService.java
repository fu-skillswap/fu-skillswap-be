package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.dto.request.AdminMentorListRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.AdminMentorListItemResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
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

@Service
@RequiredArgsConstructor
public class AdminMentorService {

    private static final String ACCENTED_CHARACTERS = "àáạảãăắằẳẵặâấầẩẫậđèéẹẻẽêếềểễệìíịỉĩòóọỏõôốồổỗộơớờởỡợùúụủũưứừửữựỳýỵỷỹ";
    private static final String PLAIN_CHARACTERS = "aaaaaaaaaaaaaaaaadeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyy";

    private final MentorProfileRepository mentorProfileRepository;

    @Transactional(readOnly = true)
    public PageResponse<AdminMentorListItemResponse> getMentors(AdminMentorListRequest request) {
        AdminMentorListRequest safeRequest = request == null ? new AdminMentorListRequest() : request;
        Page<MentorProfile> page = mentorProfileRepository.searchForAdmin(
                buildKeywordPattern(safeRequest.getKeyword()),
                buildNormalizedKeywordPattern(safeRequest.getKeyword()),
                ACCENTED_CHARACTERS,
                PLAIN_CHARACTERS,
                safeRequest.getStatus(),
                safeRequest.getIsAvailable(),
                adminMentorPageable(safeRequest)
        );

        return PageResponse.<AdminMentorListItemResponse>builder()
                .content(page.getContent().stream().map(this::toListItem).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    private AdminMentorListItemResponse toListItem(MentorProfile profile) {
        User user = profile.getUser();
        return AdminMentorListItemResponse.builder()
                .mentorUserId(profile.getUserId())
                .email(user == null ? null : user.getEmail())
                .displayName(user == null ? null : user.getFullName())
                .avatarUrl(user == null ? null : user.getAvatarUrl())
                .userStatus(user == null ? null : user.getStatus())
                .mentorStatus(profile.getStatus())
                .isAvailable(profile.isAvailable())
                .bookingSuspendedUntil(profile.getBookingSuspendedUntil())
                .headline(profile.getHeadline())
                .teachingMode(profile.getTeachingMode())
                .sessionDuration(profile.getSessionDuration())
                .ratingAverage(profile.getAverageRating())
                .reviewCount(profile.getTotalReviews())
                .completedSessions(profile.getTotalCompletedSessions())
                .rejectedBookings(profile.getTotalRejectedBookings())
                .lateCancellationPenaltyPoints(profile.getLateCancellationPenaltyPoints())
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
