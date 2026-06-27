package com.fptu.exe.skillswap.modules.academic;

import com.fptu.exe.skillswap.modules.academic.dto.request.StudentProfileRequest;
import com.fptu.exe.skillswap.modules.academic.dto.response.StudentProfileResponse;
import com.fptu.exe.skillswap.modules.academic.repository.AcademicProgramRepository;
import com.fptu.exe.skillswap.modules.academic.repository.CampusRepository;
import com.fptu.exe.skillswap.modules.academic.repository.SpecializationRepository;
import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
