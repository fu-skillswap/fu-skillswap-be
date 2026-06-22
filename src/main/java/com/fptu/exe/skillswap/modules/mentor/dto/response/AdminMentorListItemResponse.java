package com.fptu.exe.skillswap.modules.mentor.dto.response;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record AdminMentorListItemResponse(
        UUID mentorUserId,
        String displayName,
        String email,
        String avatarUrl,
        String primaryLabel,
        Integer completedSessions,
        BigDecimal ratingAverage,
        MentorStatus mentorStatus,
        LocalDateTime createdAt
) {
    // Explicit public constructor to guarantee JPQL constructor projection compatibility
    public AdminMentorListItemResponse(
            UUID mentorUserId,
            String displayName,
            String email,
            String avatarUrl,
            String primaryLabel,
            Integer completedSessions,
            BigDecimal ratingAverage,
            MentorStatus mentorStatus,
            LocalDateTime createdAt
    ) {
        this.mentorUserId = mentorUserId;
        this.displayName = displayName;
        this.email = email;
        this.avatarUrl = avatarUrl;
        this.primaryLabel = primaryLabel;
        this.completedSessions = completedSessions;
        this.ratingAverage = ratingAverage;
        this.mentorStatus = mentorStatus;
        this.createdAt = createdAt;
    }
}
