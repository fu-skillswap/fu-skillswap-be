package com.fptu.exe.skillswap.modules.identity.port.dto;

import lombok.Builder;

@Builder
public record UserSummaryAcademicDto(
        String studentCode,
        String campusCode,
        String campusName,
        String programCode,
        String programName,
        String specializationCode,
        String specializationName,
        Integer semester,
        Boolean isAlumni
) {}
