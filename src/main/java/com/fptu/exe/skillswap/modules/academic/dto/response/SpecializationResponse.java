package com.fptu.exe.skillswap.modules.academic.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin chuyên ngành (Specialization)")
public class SpecializationResponse {
    @Schema(description = "ID chuyên ngành", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID id;
    
    @Schema(description = "ID của ngành học cha", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID programId;
    
    @Schema(description = "Mã chuyên ngành", example = "KTPM")
    private String code;
    
    @Schema(description = "Tên chuyên ngành (Tiếng Việt)", example = "Kỹ thuật phần mềm")
    private String nameVi;
    
    @Schema(description = "Tên chuyên ngành (Tiếng Anh)", example = "Software Engineering")
    private String nameEn;
    
    @Schema(description = "Đây có phải là chuyên ngành hẹp (dự kiến/không chính thức) hay không", example = "false")
    private boolean isExpected;
    
    @Schema(description = "Cờ đánh dấu chuyên ngành 'Khác'", example = "false")
    private boolean isOther;
}
