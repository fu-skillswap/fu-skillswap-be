package com.fptu.exe.skillswap.modules.mentor.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Mentor verification request status used in the user verification wizard and the admin review queue.")
public enum VerificationStatus {
    DRAFT,
    PENDING_REVIEW,
    NEEDS_REVISION,
    APPROVED,
    REJECTED,
    WITHDRAWN
}
