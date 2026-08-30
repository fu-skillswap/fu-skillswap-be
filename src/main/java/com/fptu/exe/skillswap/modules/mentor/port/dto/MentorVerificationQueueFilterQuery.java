package com.fptu.exe.skillswap.modules.mentor.port.dto;

import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class MentorVerificationQueueFilterQuery extends BasePageRequest {
    private VerificationStatus status = VerificationStatus.PENDING_REVIEW;
    private String keyword;
    private LocalDateTime submittedFrom;
    private LocalDateTime submittedTo;
}
