package com.fptu.exe.skillswap.modules.academic.service;

import com.fptu.exe.skillswap.modules.academic.domain.Campus;
import com.fptu.exe.skillswap.modules.academic.domain.AcademicProgram;
import com.fptu.exe.skillswap.modules.academic.domain.Specialization;
import com.fptu.exe.skillswap.modules.academic.dto.CampusResponse;
import com.fptu.exe.skillswap.modules.academic.dto.AcademicProgramResponse;
import com.fptu.exe.skillswap.modules.academic.dto.SpecializationResponse;
import com.fptu.exe.skillswap.modules.academic.repository.CampusRepository;
import com.fptu.exe.skillswap.modules.academic.repository.AcademicProgramRepository;
import com.fptu.exe.skillswap.modules.academic.repository.SpecializationRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AcademicService {

    private final CampusRepository campusRepository;
    private final AcademicProgramRepository academicProgramRepository;
    private final SpecializationRepository specializationRepository;

    @Transactional(readOnly = true)
    public List<CampusResponse> getAllCampuses() {
        return campusRepository.findByIsActiveTrue()
                .stream()
                .map(this::mapToCampusResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AcademicProgramResponse> getAllAcademicPrograms() {
        return academicProgramRepository.findByIsActiveTrue()
                .stream()
                .map(this::mapToAcademicProgramResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SpecializationResponse> getAllSpecializations() {
        return specializationRepository.findByIsActiveTrue()
                .stream()
                .map(this::mapToSpecializationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SpecializationResponse> getSpecializationsByProgram(UUID programId) {
        if (!academicProgramRepository.existsById(programId)) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy ngành học liên kết");
        }
        return specializationRepository.findByProgramIdAndIsActiveTrue(programId)
                .stream()
                .map(this::mapToSpecializationResponse)
                .toList();
    }

    private CampusResponse mapToCampusResponse(Campus campus) {
        return CampusResponse.builder()
                .id(campus.getId())
                .code(campus.getCode())
                .name(campus.getName())
                .city(campus.getCity())
                .build();
    }

    private AcademicProgramResponse mapToAcademicProgramResponse(AcademicProgram program) {
        return AcademicProgramResponse.builder()
                .id(program.getId())
                .code(program.getCode())
                .nameVi(program.getNameVi())
                .nameEn(program.getNameEn())
                .build();
    }

    private SpecializationResponse mapToSpecializationResponse(Specialization spec) {
        return SpecializationResponse.builder()
                .id(spec.getId())
                .programId(spec.getProgram().getId())
                .code(spec.getCode())
                .nameVi(spec.getNameVi())
                .nameEn(spec.getNameEn())
                .isExpected(spec.isExpected())
                .build();
    }
}
