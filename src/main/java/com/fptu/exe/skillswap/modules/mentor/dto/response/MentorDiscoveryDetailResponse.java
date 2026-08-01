package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

/**
 * Public mentor profile in conversion order. The nested sections intentionally replace the
 * previous flat pre-launch response so FE can render a consistent profile hierarchy.
 */
@Builder
@Schema(description = "Public mentor profile arranged for conversion: identity, mentoring, services, evidence, reputation, availability.")
public record MentorDiscoveryDetailResponse(
        MentorIdentityResponse identity,
        MentorMentoringResponse mentoring,
        List<MentorServiceResponse> services,
        MentorEvidenceResponse evidence,
        MentorReputationResponse reputation,
        MentorAvailabilityResponse availability
) {
}
