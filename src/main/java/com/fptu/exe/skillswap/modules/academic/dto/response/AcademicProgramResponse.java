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
@Schema(description = "Thông tin ngành học (Academic Program)")
public class AcademicProgramResponse {
    @Schema(description = "ID ngành học", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID id;
    
    @Schema(description = "Mã ngành học", example = "CNTT")
    private String code;
    
    @Schema(description = "Tên ngành học (Tiếng Việt)", example = "Công nghệ thông tin")
    private String nameVi;
    
    @Schema(description = "Tên ngành học (Tiếng Anh)", example = "Information Technology")
    private String nameEn;
}
