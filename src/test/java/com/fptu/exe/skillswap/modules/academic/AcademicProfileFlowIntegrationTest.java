package com.fptu.exe.skillswap.modules.academic;

import com.fptu.exe.skillswap.modules.academic.dto.request.StudentProfileRequest;
import com.fptu.exe.skillswap.modules.academic.dto.response.StudentProfileResponse;
import com.fptu.exe.skillswap.modules.academic.repository.AcademicProgramRepository;
import com.fptu.exe.skillswap.modules.academic.repository.CampusRepository;
import com.fptu.exe.skillswap.modules.academic.repository.SpecializationRepository;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class AcademicProfileFlowIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AcademicService academicService;

    @Autowired
    private CampusRepository campusRepository;

    @Autowired
    private AcademicProgramRepository academicProgramRepository;

    @Autowired
    private SpecializationRepository specializationRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email("academic-flow@test.com")
                .fullName("Academic Flow User")
                .status(UserStatus.ACTIVE)
                .build());
    }

    @Test
    void updateThenGetProfile_shouldPersistNormalizedAcademicProfile() {
        var campus = campusRepository.findAll().getFirst();
        var program = academicProgramRepository.findAll().getFirst();
        var specialization = specializationRepository.findByProgramIdAndIsActiveTrue(program.getId()).getFirst();

        StudentProfileResponse updated = academicService.updateStudentProfile(user.getId(), StudentProfileRequest.builder()
                .studentCode(" se190123 ")
                .displayName("Updated Academic User")
                .avatarUrl("https://example.com/avatar.png")
                .campusId(campus.getId())
                .programId(program.getId())
                .specializationId(specialization.getId())
                .semester(5)
                .intakeYear(2022)
                .isAlumni(false)
                .bio("Backend integration acceptance flow")
                .build());

        assertEquals(user.getId(), updated.getUserId());
        assertEquals("SE190123", updated.getStudentCode());
        assertEquals("Updated Academic User", updated.getDisplayName());
        assertEquals(campus.getId(), updated.getCampus().getId());
        assertEquals(program.getId(), updated.getProgram().getId());
        assertEquals(specialization.getId(), updated.getSpecialization().getId());
        assertTrue(academicService.hasCompletedStudentProfile(user.getId()));

        StudentProfileResponse fetched = academicService.getStudentProfile(user.getId());
        assertEquals(updated.getUserId(), fetched.getUserId());
        assertEquals(updated.getStudentCode(), fetched.getStudentCode());
        assertEquals(updated.getDisplayName(), fetched.getDisplayName());
        assertEquals(updated.getAvatarUrl(), fetched.getAvatarUrl());
        assertEquals(updated.getBio(), fetched.getBio());
    }

    @Test
    void incrementEligibleSemesters_shouldSkipPreparatoryAlumniAndCappedProfiles() {
        var campus = campusRepository.findAll().getFirst();
        var program = academicProgramRepository.findAll().getFirst();
        var specialization = specializationRepository.findByProgramIdAndIsActiveTrue(program.getId()).getFirst();

        User preparatory = createUser("semester-zero@test.com");
        User active = createUser("semester-eight@test.com");
        User capped = createUser("semester-nine@test.com");
        User alumni = createUser("semester-alumni@test.com");

        academicService.updateStudentProfile(preparatory.getId(), profileRequest(campus.getId(), program.getId(), specialization.getId(), "SE190124", 0, false));
        academicService.updateStudentProfile(active.getId(), profileRequest(campus.getId(), program.getId(), specialization.getId(), "SE190125", 8, false));
        academicService.updateStudentProfile(capped.getId(), profileRequest(campus.getId(), program.getId(), specialization.getId(), "SE190126", 9, false));
        academicService.updateStudentProfile(alumni.getId(), profileRequest(campus.getId(), program.getId(), specialization.getId(), "SE190127", 5, true));

        int updatedCount = academicService.incrementEligibleSemesters();

        assertEquals(1, updatedCount);
        assertEquals(0, studentProfileRepository.findById(preparatory.getId()).orElseThrow().getSemester());
        assertEquals(9, studentProfileRepository.findById(active.getId()).orElseThrow().getSemester());
        assertEquals(9, studentProfileRepository.findById(capped.getId()).orElseThrow().getSemester());
        assertEquals(9, studentProfileRepository.findById(alumni.getId()).orElseThrow().getSemester());
    }

    @Test
    void updateStudentProfile_shouldRejectGraduationYearLessThanIntakePlusTwoYears() {
        var campus = campusRepository.findAll().getFirst();
        var program = academicProgramRepository.findAll().getFirst();
        var specialization = specializationRepository.findByProgramIdAndIsActiveTrue(program.getId()).getFirst();

        BaseException exception = assertThrows(BaseException.class, () -> academicService.updateStudentProfile(
                user.getId(),
                StudentProfileRequest.builder()
                        .studentCode("SE190128")
                        .campusId(campus.getId())
                        .programId(program.getId())
                        .specializationId(specialization.getId())
                        .semester(9)
                        .intakeYear(2024)
                        .isAlumni(true)
                        .graduationYear(2025)
                        .bio("Invalid graduation timeline")
                        .build()
        ));

        assertEquals("Năm tốt nghiệp phải lớn hơn năm nhập học ít nhất 2 năm", exception.getMessage());
    }

    private User createUser(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .fullName(email)
                .status(UserStatus.ACTIVE)
                .build());
    }

    private StudentProfileRequest profileRequest(
            java.util.UUID campusId,
            java.util.UUID programId,
            java.util.UUID specializationId,
            String studentCode,
            int semester,
            boolean alumni
    ) {
        return StudentProfileRequest.builder()
                .studentCode(studentCode)
                .campusId(campusId)
                .programId(programId)
                .specializationId(specializationId)
                .semester(semester)
                .intakeYear(2022)
                .isAlumni(alumni)
                .graduationYear(alumni ? 2025 : null)
                .bio("Semester increment test")
                .build();
    }
}
