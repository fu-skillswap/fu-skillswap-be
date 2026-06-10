package com.fptu.exe.skillswap.modules.academic.service;

import com.fptu.exe.skillswap.modules.academic.domain.Campus;
import com.fptu.exe.skillswap.modules.academic.domain.AcademicProgram;
import com.fptu.exe.skillswap.modules.academic.domain.Specialization;
import com.fptu.exe.skillswap.modules.academic.dto.CampusResponse;
import com.fptu.exe.skillswap.modules.academic.dto.AcademicProgramResponse;
import com.fptu.exe.skillswap.modules.academic.dto.SpecializationResponse;
import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.academic.dto.StudentProfileRequest;
import com.fptu.exe.skillswap.modules.academic.dto.StudentProfileResponse;
import com.fptu.exe.skillswap.modules.academic.repository.CampusRepository;
import com.fptu.exe.skillswap.modules.academic.repository.AcademicProgramRepository;
import com.fptu.exe.skillswap.modules.academic.repository.SpecializationRepository;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.shared.event.ProfileStatusQuery;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
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
    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;

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
                .isOther(spec.getCode().endsWith("_OTHER"))
                .build();
    }

    @Transactional(readOnly = true)
    public StudentProfileResponse getStudentProfile(UUID userId) {
        StudentProfile profile = studentProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Hồ sơ học thuật chưa được tạo"));
        return mapToStudentProfileResponse(profile);
    }

    @Transactional
    public StudentProfileResponse updateStudentProfile(UUID userId, StudentProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        Campus campus = campusRepository.findById(request.getCampusId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy cơ sở học tập"));

        AcademicProgram program = academicProgramRepository.findById(request.getProgramId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy ngành học"));

        Specialization specialization = specializationRepository.findById(request.getSpecializationId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy chuyên ngành"));

        // Validate specialization belongs to program
        if (!specialization.getProgram().getId().equals(program.getId())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Chuyên ngành không thuộc ngành học đã chọn");
        }

        // Validate unique student code
        String normalizedStudentCode = request.getStudentCode().trim().toUpperCase();
        if (studentProfileRepository.existsByStudentCodeAndUserIdNot(normalizedStudentCode, userId)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã số sinh viên đã tồn tại trên hệ thống");
        }

        // Update user fields if provided
        if (request.getDisplayName() != null && !request.getDisplayName().trim().isEmpty()) {
            user.setFullName(request.getDisplayName().trim());
        }
        if (request.getAvatarUrl() != null && !request.getAvatarUrl().trim().isEmpty()) {
            user.setAvatarUrl(request.getAvatarUrl().trim());
        }
        userRepository.save(user);

        // Update or create StudentProfile
        StudentProfile profile = studentProfileRepository.findById(userId)
                .orElseGet(() -> StudentProfile.builder()
                        .userId(userId)
                        .user(user)
                        .build());

        profile.setStudentCode(normalizedStudentCode);
        profile.setCampus(campus);
        profile.setProgram(program);
        profile.setSpecialization(specialization);
        profile.setSemester(request.getSemester());
        profile.setIntakeYear(request.getIntakeYear());
        profile.setAlumni(request.getIsAlumni());
        profile.setGraduationYear(request.getGraduationYear());
        profile.setBio(request.getBio());

        StudentProfile savedProfile = studentProfileRepository.save(profile);
        return mapToStudentProfileResponse(savedProfile);
    }

    /**
     * Lắng nghe ProfileStatusQuery từ module identity.
     * Set kết quả hasStudentProfile vào event object (synchronous request-reply pattern).
     */
    @EventListener
    public void onProfileStatusQuery(ProfileStatusQuery query) {
        boolean exists = studentProfileRepository.existsById(query.getUserId());
        query.setHasStudentProfile(exists);
    }

    private StudentProfileResponse mapToStudentProfileResponse(StudentProfile profile) {
        User user = profile.getUser();
        return StudentProfileResponse.builder()
                .userId(profile.getUserId())
                .email(user.getEmail())
                .studentCode(profile.getStudentCode())
                .displayName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .campus(mapToCampusResponse(profile.getCampus()))
                .program(mapToAcademicProgramResponse(profile.getProgram()))
                .specialization(mapToSpecializationResponse(profile.getSpecialization()))
                .semester(profile.getSemester())
                .intakeYear(profile.getIntakeYear())
                .isAlumni(profile.isAlumni())
                .graduationYear(profile.getGraduationYear())
                .bio(profile.getBio())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}
