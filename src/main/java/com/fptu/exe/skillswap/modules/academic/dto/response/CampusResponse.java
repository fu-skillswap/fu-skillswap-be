package com.fptu.exe.skillswap.modules.academic.dto.response;

import com.fptu.exe.skillswap.modules.academic.domain.CampusCode;
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
@Schema(description = "Thông tin cơ sở đào tạo của FPT University")
public class CampusResponse {
    @Schema(description = "ID của cơ sở", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID id;
    
    @Schema(description = "Mã cơ sở", example = "HCM")
    private CampusCode code;
    
    @Schema(description = "Tên đầy đủ của cơ sở", example = "FPT University Hồ Chí Minh")
    private String name;
    
    @Schema(description = "Thành phố trực thuộc", example = "Hồ Chí Minh")
    private String city;
}
