package com.fptu.exe.skillswap.modules.identity.port;

import java.util.UUID;

/** Minimal academic read model needed by other business modules. */
public interface AcademicEligibilityQuery {

    boolean hasCompletedStudentProfile(UUID userId);
}
