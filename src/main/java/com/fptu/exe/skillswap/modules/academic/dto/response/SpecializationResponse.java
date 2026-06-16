package com.fptu.exe.skillswap.modules.academic.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpecializationResponse {
    private UUID id;
    private UUID programId;
    private String code;
    private String nameVi;
    private String nameEn;
    private boolean isExpected;
    private boolean isOther;
}
