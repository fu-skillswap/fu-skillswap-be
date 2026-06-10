package com.fptu.exe.skillswap.modules.academic.controller;

import com.fptu.exe.skillswap.modules.academic.dto.CampusResponse;
import com.fptu.exe.skillswap.modules.academic.dto.AcademicProgramResponse;
import com.fptu.exe.skillswap.modules.academic.dto.SpecializationResponse;
import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AcademicController {

    private final AcademicService academicService;

    @GetMapping("/campuses")
    public ApiResponse<List<CampusResponse>> getCampuses() {
        List<CampusResponse> campuses = academicService.getAllCampuses();
        return ApiResponse.success(campuses);
    }

    @GetMapping("/academic-programs")
    public ApiResponse<List<AcademicProgramResponse>> getAcademicPrograms() {
        List<AcademicProgramResponse> programs = academicService.getAllAcademicPrograms();
        return ApiResponse.success(programs);
    }

    @GetMapping("/specializations")
    public ApiResponse<List<SpecializationResponse>> getSpecializations() {
        List<SpecializationResponse> specs = academicService.getAllSpecializations();
        return ApiResponse.success(specs);
    }

    @GetMapping("/academic-programs/{programId}/specializations")
    public ApiResponse<List<SpecializationResponse>> getSpecializationsByProgram(@PathVariable UUID programId) {
        List<SpecializationResponse> specs = academicService.getSpecializationsByProgram(programId);
        return ApiResponse.success(specs);
    }
}
