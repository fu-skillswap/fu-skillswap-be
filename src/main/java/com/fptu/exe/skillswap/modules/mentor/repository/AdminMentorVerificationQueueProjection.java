package com.fptu.exe.skillswap.modules.mentor.repository;

import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AdminMentorVerificationQueueProjection {

    UUID getRequestId();

    UUID getMentorUserId();

    String getMentorEmail();

    String getMentorFullName();

    String getMentorAvatarUrl();

    VerificationStatus getStatus();

    Integer getRevisionCount();

    LocalDateTime getSubmittedAt();

    LocalDateTime getCreatedAt();

    LocalDateTime getUpdatedAt();
}
