package com.fptu.exe.skillswap.modules.identity.dto.response;

import com.fptu.exe.skillswap.modules.identity.domain.StudentProfileType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin hồ sơ người học")
public class StudentProfileResponse {
    private UUID id;
    private UUID userId;

    @Schema(description = "Current learner profile type; null means the legacy profile must be migrated.", nullable = true)
    private StudentProfileType profileType;
    private UUID institutionId;
    private String customInstitutionName;
    private UUID customInstitutionProvinceId;
    private UUID fieldGroupId;
    private String majorName;
    private Integer enrollmentYear;
    private Integer graduationYear;
    private boolean onboardingCompleted;
    private LocalDateTime onboardingCompletedAt;

    private String bio;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
