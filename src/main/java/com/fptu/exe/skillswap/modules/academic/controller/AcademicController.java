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
@Tag(name = "Academic Catalog", description = "Nhóm API trả dữ liệu danh mục campus, program và specialization để điền form onboarding hoặc form cập nhật hồ sơ học thuật. FE dùng các API này để đổ dropdown trước khi lưu Academic Profile.")
public class AcademicController {

    private final AcademicService academicService;

    @Operation(summary = "Lấy danh sách campus", description = "Trả về danh sách campus đang hoạt động của FPT University dùng trong form hồ sơ học thuật. FE dùng để đổ dropdown chọn campus ở bước onboarding hoặc màn cập nhật profile.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Danh sách cơ sở")
    })
    @GetMapping("/campuses")
    public ApiResponse<List<CampusResponse>> getCampuses() {
        List<CampusResponse> campuses = academicService.getAllCampuses();
        return ApiResponse.success(campuses);
    }

    @Operation(summary = "Lấy danh sách academic programs", description = "Trả về danh sách academic programs đang hoạt động dùng trong form hồ sơ học thuật. FE dùng để đổ dropdown chọn program trước khi user chọn specialization.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Danh sách ngành học")
    })
    @GetMapping("/academic-programs")
    public ApiResponse<List<AcademicProgramResponse>> getAcademicPrograms() {
        List<AcademicProgramResponse> programs = academicService.getAllAcademicPrograms();
        return ApiResponse.success(programs);
    }

    @Operation(summary = "Lấy toàn bộ specialization", description = "Trả về toàn bộ specialization đang hoạt động của tất cả program. FE chỉ nên dùng khi cần full dataset specialization thay vì danh sách theo từng program cụ thể.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Danh sách chuyên ngành")
    })
    @GetMapping("/specializations")
    public ApiResponse<List<SpecializationResponse>> getSpecializations() {
        List<SpecializationResponse> specs = academicService.getAllSpecializations();
        return ApiResponse.success(specs);
    }

    @Operation(summary = "Lấy specialization theo program", description = "Trả về danh sách specialization đang hoạt động thuộc về một academic program cụ thể. FE dùng sau khi user chọn program trong onboarding hoặc màn sửa academic profile.")
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
