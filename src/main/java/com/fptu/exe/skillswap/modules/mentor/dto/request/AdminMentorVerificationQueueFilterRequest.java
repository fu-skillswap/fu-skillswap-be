package com.fptu.exe.skillswap.modules.mentor.dto.request;

import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminMentorVerificationQueueFilterRequest extends BasePageRequest {

    private VerificationStatus status = VerificationStatus.PENDING_REVIEW;
    private String keyword;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime submittedFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime submittedTo;

    public AdminMentorVerificationQueueFilterRequest() {
        setSortBy("submittedAt");
        setDirection(Sort.Direction.DESC);
        setSize(20);
    }
}
