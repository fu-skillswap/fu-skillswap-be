package com.fptu.exe.skillswap.modules.identity.port;

import java.util.UUID;

public record StudentProfileRecord(
        UUID userId,
        String studentCode,
        UUID campusId,
        String campusName,
        UUID programId,
        String programCode,
        String programName,
        UUID specializationId,
        String specializationCode,
        String specializationName,
        Integer semester,
        Integer intakeYear,
        Boolean isAlumni,
        String bio
) {}
