package com.fptu.exe.skillswap.modules.academic.dto.response;

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
public class StudentProfileResponse {
    private UUID userId;
    private String email;
    private String studentCode;
    private String displayName;
    private String avatarUrl;
    private CampusResponse campus;
    private AcademicProgramResponse program;
    private SpecializationResponse specialization;
    private Integer semester;
    private Integer intakeYear;
    private boolean isAlumni;
    private Integer graduationYear;
    private String bio;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
