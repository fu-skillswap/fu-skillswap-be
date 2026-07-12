package com.fptu.exe.skillswap.modules.academic.service;

import com.fptu.exe.skillswap.modules.academic.domain.Campus;
import com.fptu.exe.skillswap.modules.academic.domain.AcademicProgram;
import com.fptu.exe.skillswap.modules.academic.domain.Specialization;
import com.fptu.exe.skillswap.modules.academic.dto.response.CampusResponse;
import com.fptu.exe.skillswap.modules.academic.dto.response.AcademicProgramResponse;
import com.fptu.exe.skillswap.modules.academic.dto.response.SpecializationResponse;
import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.academic.dto.request.StudentProfileRequest;
import com.fptu.exe.skillswap.modules.academic.dto.response.StudentProfileResponse;
import com.fptu.exe.skillswap.modules.academic.repository.CampusRepository;
import com.fptu.exe.skillswap.modules.academic.repository.AcademicProgramRepository;
import com.fptu.exe.skillswap.modules.academic.repository.SpecializationRepository;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.shared.event.ProfileStatusQuery;
import com.fptu.exe.skillswap.shared.event.UserDeletedEvent;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import java.time.Year;
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
    @Cacheable(cacheNames = "catalog", key = "'campuses'")
    public List<CampusResponse> getAllCampuses() {
        return campusRepository.findByIsActiveTrue()
                .stream()
                .map(this::mapToCampusResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "catalog", key = "'programs'")
    public List<AcademicProgramResponse> getAllAcademicPrograms() {
        return academicProgramRepository.findByIsActiveTrue()
                .stream()
                .map(this::mapToAcademicProgramResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "catalog", key = "'specializations'")
    public List<SpecializationResponse> getAllSpecializations() {
        return specializationRepository.findByIsActiveTrue()
                .stream()
                .map(this::mapToSpecializationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "catalog", key = "'specializationsByProgram:' + #programId")
    public List<SpecializationResponse> getSpecializationsByProgram(UUID programId) {
        requireId(programId, "Ngành học");
        if (!academicProgramRepository.existsByIdAndIsActiveTrue(programId)) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy ngành học liên kết");
        }
        return specializationRepository.findByProgramIdAndIsActiveTrue(programId)
                .stream()
                .map(this::mapToSpecializationResponse)
                .toList();
    }

    @org.springframework.cache.annotation.CacheEvict(cacheNames = "catalog", allEntries = true)
    public void evictCatalogCache() {
        log.info("Catalog cache evicted manually");
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
        requireId(userId, "Người dùng");
        StudentProfile profile = studentProfileRepository.findWithDetailsByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Hồ sơ học thuật chưa được tạo"));
        return mapToStudentProfileResponse(profile);
    }

    @Transactional(readOnly = true)
    public boolean hasCompletedStudentProfile(UUID userId) {
        requireId(userId, "Người dùng");
        return studentProfileRepository.findWithDetailsByUserId(userId)
                .map(this::isProfileCompleted)
                .orElse(false);
    }

    @Transactional
    public StudentProfileResponse updateStudentProfile(UUID userId, StudentProfileRequest request) {
        requireId(userId, "Người dùng");
        requireStudentProfileRequest(request);
        validateAcademicTimeline(request);
        Campus campus = campusRepository.findByIdAndIsActiveTrue(request.getCampusId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy cơ sở học tập"));
        AcademicProgram program = academicProgramRepository.findByIdAndIsActiveTrue(request.getProgramId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy ngành học"));
        Specialization specialization = specializationRepository.findByIdAndIsActiveTrue(request.getSpecializationId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy chuyên ngành"));
        validateAcademicRelation(program, specialization);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        // Normalize student code
        String normalizedStudentCode = request.getStudentCode().trim().toUpperCase();

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
                .orElseGet(() -> {
                    StudentProfile studentProfile = new StudentProfile();
                    studentProfile.setUser(user);
                    return studentProfile;
                });

        profile.setClaimedStudentCode(normalizedStudentCode);
        profile.setCampus(campus);
        profile.setProgram(program);
        profile.setSpecialization(specialization);
        boolean alumni = Boolean.TRUE.equals(request.getIsAlumni());
        profile.setSemester(resolveSemester(request.getSemester(), alumni));
        profile.setIntakeYear(request.getIntakeYear());
        profile.setAlumni(alumni);
        profile.setGraduationYear(request.getGraduationYear());
        profile.setBio(request.getBio());

        StudentProfile savedProfile = studentProfileRepository.save(profile);
        return mapToStudentProfileResponse(savedProfile);
    }

    @Transactional
    public int incrementEligibleSemesters() {
        int updatedCount = studentProfileRepository.incrementEligibleSemesters(DateTimeUtil.now());
        if (updatedCount > 0) {
            log.info("Incremented semester for {} eligible student profiles", updatedCount);
        } else {
            log.info("No eligible student profiles for semester increment");
        }
        return updatedCount;
    }

    /**
     * Lắng nghe ProfileStatusQuery từ module identity.
     * Set kết quả hasStudentProfile vào event object (synchronous request-reply pattern).
     */
    @EventListener
    public void onProfileStatusQuery(ProfileStatusQuery query) {
        query.setHasStudentProfile(hasCompletedStudentProfile(query.getUserId()));
    }

    private StudentProfileResponse mapToStudentProfileResponse(StudentProfile profile) {
        User user = profile.getUser();
        return StudentProfileResponse.builder()
                .userId(profile.getUserId())
                .email(user.getEmail())
                .studentCode(profile.getClaimedStudentCode())
                .displayName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .campus(profile.getCampus() == null ? null : mapToCampusResponse(profile.getCampus()))
                .program(profile.getProgram() == null ? null : mapToAcademicProgramResponse(profile.getProgram()))
                .specialization(profile.getSpecialization() == null ? null : mapToSpecializationResponse(profile.getSpecialization()))
                .semester(profile.getSemester())
                .intakeYear(profile.getIntakeYear())
                .isAlumni(profile.isAlumni())
                .graduationYear(profile.getGraduationYear())
                .bio(profile.getBio())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    private void requireId(UUID id, String label) {
        if (id == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, label + " không được để trống");
        }
    }

    private void requireStudentProfileRequest(StudentProfileRequest request) {
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Dữ liệu hồ sơ học thuật không được để trống");
        }
    }

    private void validateAcademicRelation(AcademicProgram program, Specialization specialization) {
        if (program == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Ngành học không được để trống");
        }
        if (specialization == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Chuyên ngành không được để trống");
        }
        if (specialization.getProgram() == null || !specialization.getProgram().getId().equals(program.getId())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Chuyên ngành không thuộc ngành học đã chọn");
        }
    }

    private boolean isProfileCompleted(StudentProfile profile) {
        if (profile == null) {
            return false;
        }
        if (profile.getCampus() == null
                || profile.getProgram() == null
                || profile.getSpecialization() == null
                || profile.getSemester() == null
                || profile.getIntakeYear() == null
                || profile.getClaimedStudentCode() == null
                || profile.getClaimedStudentCode().isBlank()) {
            return false;
        }
        return !profile.isAlumni() || profile.getGraduationYear() != null;
    }

    private void validateAcademicTimeline(StudentProfileRequest request) {
        int currentYear = Year.now().getValue();

        if (request.getSemester() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Học kỳ không được để trống");
        }
        if (request.getSemester() != null && request.getSemester() < 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Học kỳ không được nhỏ hơn 0");
        }
        if (request.getSemester() != null && request.getSemester() > 9) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Học kỳ không được lớn hơn 9");
        }

        if (request.getIntakeYear() != null
                && (request.getIntakeYear() < 2000 || request.getIntakeYear() > currentYear)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Năm nhập học không hợp lệ");
        }

        if (Boolean.TRUE.equals(request.getIsAlumni()) && request.getGraduationYear() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cựu sinh viên bắt buộc phải có năm tốt nghiệp");
        }

        if (request.getGraduationYear() != null) {
            if (request.getGraduationYear() < 2000 || request.getGraduationYear() > currentYear) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Năm tốt nghiệp không hợp lệ");
            }
            if (request.getIntakeYear() != null && request.getGraduationYear() < request.getIntakeYear() + 2) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Năm tốt nghiệp phải lớn hơn năm nhập học ít nhất 2 năm");
            }
        }
    }

    private int resolveSemester(Integer requestedSemester, boolean alumni) {
        if (alumni) {
            return 9;
        }
        return requestedSemester;
    }

    @EventListener
    @Transactional
    public void onUserDeleted(UserDeletedEvent event) {
        log.info("Academic module: Handling UserDeletedEvent for user: {}", event.getUserId());
        if (studentProfileRepository.existsById(event.getUserId())) {
            studentProfileRepository.deleteById(event.getUserId());
            log.info("Deleted StudentProfile for user: {}", event.getUserId());
        }
    }
}
