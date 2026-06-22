package com.fptu.exe.skillswap.modules.academic.controller;

import com.fptu.exe.skillswap.modules.academic.dto.response.CampusResponse;
import com.fptu.exe.skillswap.modules.academic.dto.response.AcademicProgramResponse;
import com.fptu.exe.skillswap.modules.academic.dto.response.SpecializationResponse;
import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Academic Catalog", description = "Lookups for campus list, academic programs, specializations, and help topics")
public class AcademicController {

    private final AcademicService academicService;

    @Operation(summary = "Lấy danh sách cơ sở", description = "Trả về tất cả cơ sở đang hoạt động của FPT University (Hà Nội, HCM, Đà Nẵng, Cần Thơ, Quy Nhơn). "
            +
            "Dùng để hiển thị dropdown chọn cơ sở khi điền hồ sơ học thuật.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Danh sách cơ sở")
    })
    @GetMapping("/campuses")
    public ApiResponse<List<CampusResponse>> getCampuses() {
        List<CampusResponse> campuses = academicService.getAllCampuses();
        return ApiResponse.success(campuses);
    }

    @Operation(summary = "Lấy danh sách ngành học", description = "Trả về tất cả ngành học đang hoạt động (ví dụ: Công nghệ thông tin, Ngôn ngữ, Luật, ...). "
            +
            "Dùng để hiển thị dropdown chọn ngành khi điền hồ sơ học thuật.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Danh sách ngành học")
    })
    @GetMapping("/academic-programs")
    public ApiResponse<List<AcademicProgramResponse>> getAcademicPrograms() {
        List<AcademicProgramResponse> programs = academicService.getAllAcademicPrograms();
        return ApiResponse.success(programs);
    }

    @Operation(summary = "Lấy tất cả chuyên ngành", description = "Trả về toàn bộ chuyên ngành đang hoạt động của tất cả ngành học. "
            +
            "Nếu chỉ cần chuyên ngành theo ngành cụ thể, hãy dùng API `/academic-programs/{programId}/specializations`.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Danh sách chuyên ngành")
    })
    @GetMapping("/specializations")
    public ApiResponse<List<SpecializationResponse>> getSpecializations() {
        List<SpecializationResponse> specs = academicService.getAllSpecializations();
        return ApiResponse.success(specs);
    }

    @Operation(summary = "Lấy chuyên ngành theo ngành học", description = "Trả về danh sách chuyên ngành thuộc một ngành học cụ thể. "
            +
            "Dùng để lọc chuyên ngành sau khi người dùng đã chọn ngành học.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Danh sách chuyên ngành"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy ngành học với ID đã cho")
    })
    @GetMapping("/academic-programs/{programId}/specializations")
    public ApiResponse<List<SpecializationResponse>> getSpecializationsByProgram(
            @Parameter(description = "ID của ngành học", required = true, example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") @PathVariable UUID programId) {
        List<SpecializationResponse> specs = academicService.getSpecializationsByProgram(programId);
        return ApiResponse.success(specs);
    }
}
