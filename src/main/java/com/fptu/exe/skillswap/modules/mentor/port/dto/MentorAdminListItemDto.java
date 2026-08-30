package com.fptu.exe.skillswap.modules.mentor.port.dto;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record MentorAdminListItemDto(
        UUID mentorUserId,
        String fullName,
        String email,
        String avatarUrl,
        String programCode,
        Integer totalCompletedSessions,
        BigDecimal averageRating,
        MentorStatus status,
        LocalDateTime createdAt
) {
    public MentorAdminListItemDto(
            UUID mentorUserId,
            String fullName,
            String email,
            String avatarUrl,
            String programCode,
            Integer totalCompletedSessions,
            BigDecimal averageRating,
            MentorStatus status,
            LocalDateTime createdAt
    ) {
        this.mentorUserId = mentorUserId;
        this.fullName = fullName;
        this.email = email;
        this.avatarUrl = avatarUrl;
        this.programCode = programCode;
        this.totalCompletedSessions = totalCompletedSessions;
        this.averageRating = averageRating;
        this.status = status;
        this.createdAt = createdAt;
    }
}
