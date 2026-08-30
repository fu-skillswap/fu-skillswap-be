package com.fptu.exe.skillswap.modules.identity.port.dto;

import lombok.Builder;

@Builder
public record UserAdminAcademicDto(
        String claimedStudentCode
) {}
