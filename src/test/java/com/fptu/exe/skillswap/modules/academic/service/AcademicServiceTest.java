package com.fptu.exe.skillswap.modules.academic.service;

import com.fptu.exe.skillswap.modules.academic.domain.AcademicProgram;
import com.fptu.exe.skillswap.modules.academic.domain.Campus;
import com.fptu.exe.skillswap.modules.academic.domain.Specialization;
import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.academic.dto.request.StudentProfileRequest;
import com.fptu.exe.skillswap.modules.academic.dto.response.StudentProfileResponse;
import com.fptu.exe.skillswap.modules.academic.repository.AcademicProgramRepository;
import com.fptu.exe.skillswap.modules.academic.repository.CampusRepository;
import com.fptu.exe.skillswap.modules.academic.repository.SpecializationRepository;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcademicServiceTest {

    @Mock
    private CampusRepository campusRepository;
    @Mock
    private AcademicProgramRepository academicProgramRepository;
    @Mock
    private SpecializationRepository specializationRepository;
    @Mock
    private StudentProfileRepository studentProfileRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AcademicService academicService;

    private UUID userId;
    private User user;
    private StudentProfileRequest request;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        user.setEmail("student@test.com");

        request = new StudentProfileRequest();
        request.setCampusId(UUID.randomUUID());
        request.setProgramId(UUID.randomUUID());
        request.setSpecializationId(UUID.randomUUID());
        request.setStudentCode(" se123456 "); // To test normalization
        request.setSemester(5);
        request.setIntakeYear(2021);
        request.setIsAlumni(false);

        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        
        Campus campus = new Campus();
        campus.setId(request.getCampusId());
        lenient().when(campusRepository.findById(request.getCampusId())).thenReturn(Optional.of(campus));

        AcademicProgram program = new AcademicProgram();
        program.setId(request.getProgramId());
        lenient().when(academicProgramRepository.findById(request.getProgramId())).thenReturn(Optional.of(program));

        Specialization specialization = new Specialization();
        specialization.setId(request.getSpecializationId());
        specialization.setCode("SE");
        specialization.setProgram(program);
        lenient().when(specializationRepository.findById(request.getSpecializationId())).thenReturn(Optional.of(specialization));
    }

    @Test
    void createStudentProfile_shouldAllowDuplicateClaimedStudentCode() {
        when(studentProfileRepository.findById(userId)).thenReturn(Optional.empty());
        when(studentProfileRepository.save(any(StudentProfile.class))).thenAnswer(i -> i.getArgument(0));

        StudentProfileResponse response = academicService.updateStudentProfile(userId, request);

        assertNotNull(response);
        assertEquals("SE123456", response.getStudentCode());
        verify(studentProfileRepository).save(any(StudentProfile.class));
    }

    @Test
    void createStudentProfile_shouldNormalizeClaimedStudentCode() {
        when(studentProfileRepository.findById(userId)).thenReturn(Optional.empty());
        when(studentProfileRepository.save(any(StudentProfile.class))).thenAnswer(i -> {
            StudentProfile p = i.getArgument(0);
            assertEquals("SE123456", p.getClaimedStudentCode());
            return p;
        });

        StudentProfileResponse response = academicService.updateStudentProfile(userId, request);

        assertNotNull(response);
        assertEquals("SE123456", response.getStudentCode());
    }

    @Test
    void createStudentProfile_shouldOnlyPersistClaimedStudentCodeOnOnboarding() {
        when(studentProfileRepository.findById(userId)).thenReturn(Optional.empty());
        when(studentProfileRepository.save(any(StudentProfile.class))).thenAnswer(i -> {
            StudentProfile p = i.getArgument(0);
            assertEquals("SE123456", p.getClaimedStudentCode(), "Claimed student code should be normalized and persisted");
            return p;
        });

        academicService.updateStudentProfile(userId, request);
    }

    @Test
    void studentProfileResponse_shouldPreserveStudentCodeForFrontend() {
        StudentProfile existingProfile = new StudentProfile();
        existingProfile.setUserId(userId);
        existingProfile.setUser(user);
        existingProfile.setClaimedStudentCode("SE999999");
        when(studentProfileRepository.findWithDetailsByUserId(userId)).thenReturn(Optional.of(existingProfile));

        StudentProfileResponse response = academicService.getStudentProfile(userId);

        assertNotNull(response);
        assertEquals("SE999999", response.getStudentCode(), "Response must map claimedStudentCode back to studentCode for frontend compatibility");
    }
}
