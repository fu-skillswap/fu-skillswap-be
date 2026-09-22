package com.fptu.exe.skillswap.modules.identity.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fptu.exe.skillswap.modules.identity.domain.StudentProfileType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Canonical multi-institution student profile payload.")
public class StudentProfileRequest {
    @Schema(description = "Required profile model discriminator.", requiredMode = Schema.RequiredMode.REQUIRED)
    private StudentProfileType profileType;

    private UUID institutionId;

    @Size(max = 200, message = "Tên trường không được quá 200 ký tự")
    private String customInstitutionName;

    private UUID customInstitutionProvinceId;
    private UUID fieldGroupId;

    @Size(max = 200, message = "Chuyên ngành không được quá 200 ký tự")
    private String majorName;

    @Min(value = 1900, message = "Năm nhập học không hợp lệ")
    @Max(value = 2200, message = "Năm nhập học không hợp lệ")
    private Integer enrollmentYear;

    @Min(value = 1900, message = "Năm tốt nghiệp không hợp lệ")
    @Max(value = 2200, message = "Năm tốt nghiệp không hợp lệ")
    private Integer graduationYear;

    @Schema(description = "General student profile biography.", nullable = true)
    private String bio;

    @Builder.Default
    @Schema(hidden = true)
    private Map<String, Object> unsupportedFields = new LinkedHashMap<>();

    @JsonAnySetter
    public void captureUnsupportedField(String name, Object value) {
        if (unsupportedFields == null) unsupportedFields = new LinkedHashMap<>();
        unsupportedFields.put(name, value);
    }
}
