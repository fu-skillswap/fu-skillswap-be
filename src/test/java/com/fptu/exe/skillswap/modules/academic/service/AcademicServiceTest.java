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
import com.fptu.exe.skillswap.shared.exception.BaseException;
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
        campus.setActive(true);
        lenient().when(campusRepository.findByIdAndIsActiveTrue(request.getCampusId())).thenReturn(Optional.of(campus));

        AcademicProgram program = new AcademicProgram();
        program.setId(request.getProgramId());
        program.setActive(true);
        lenient().when(academicProgramRepository.findByIdAndIsActiveTrue(request.getProgramId())).thenReturn(Optional.of(program));

        Specialization specialization = new Specialization();
        specialization.setId(request.getSpecializationId());
        specialization.setCode("SE");
        specialization.setProgram(program);
        specialization.setActive(true);
        lenient().when(specializationRepository.findByIdAndIsActiveTrue(request.getSpecializationId())).thenReturn(Optional.of(specialization));
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

    @Test
    void updateStudentProfile_whenSemesterGreaterThanNine_shouldReject() {
        request.setSemester(10);

        BaseException exception = assertThrows(BaseException.class,
                () -> academicService.updateStudentProfile(userId, request));

        assertEquals("Học kỳ không được lớn hơn 9", exception.getMessage());
        verify(studentProfileRepository, never()).save(any(StudentProfile.class));
    }

    @Test
    void updateStudentProfile_whenAlumni_shouldForceSemesterNine() {
        request.setIsAlumni(true);
        request.setSemester(5);
        request.setGraduationYear(2024);
        when(studentProfileRepository.findById(userId)).thenReturn(Optional.empty());
        when(studentProfileRepository.save(any(StudentProfile.class))).thenAnswer(i -> {
            StudentProfile profile = i.getArgument(0);
            assertTrue(profile.isAlumni());
            assertEquals(9, profile.getSemester());
            return profile;
        });

        StudentProfileResponse response = academicService.updateStudentProfile(userId, request);

        assertTrue(response.isAlumni());
        assertEquals(9, response.getSemester());
    }

    @Test
    void updateStudentProfile_whenSpecializationDoesNotBelongToProgram_shouldReject() {
        UUID otherProgramId = UUID.randomUUID();
        AcademicProgram otherProgram = new AcademicProgram();
        otherProgram.setId(otherProgramId);
        otherProgram.setActive(true);

        Specialization mismatchedSpecialization = new Specialization();
        mismatchedSpecialization.setId(request.getSpecializationId());
        mismatchedSpecialization.setCode("SE");
        AcademicProgram anotherProgram = new AcademicProgram();
        anotherProgram.setId(UUID.randomUUID());
        anotherProgram.setActive(true);
        mismatchedSpecialization.setProgram(anotherProgram);
        mismatchedSpecialization.setActive(true);

        when(academicProgramRepository.findByIdAndIsActiveTrue(request.getProgramId())).thenReturn(Optional.of(otherProgram));
        when(specializationRepository.findByIdAndIsActiveTrue(request.getSpecializationId())).thenReturn(Optional.of(mismatchedSpecialization));
        when(campusRepository.findByIdAndIsActiveTrue(request.getCampusId())).thenReturn(Optional.of(new Campus()));

        BaseException exception = assertThrows(BaseException.class,
                () -> academicService.updateStudentProfile(userId, request));

        assertEquals("Chuyên ngành không thuộc ngành học đã chọn", exception.getMessage());
        verify(studentProfileRepository, never()).save(any(StudentProfile.class));
    }

    @Test
    void incrementEligibleSemesters_shouldDelegateBulkUpdate() {
        when(studentProfileRepository.incrementEligibleSemesters(any())).thenReturn(3);

        int updatedCount = academicService.incrementEligibleSemesters();

        assertEquals(3, updatedCount);
        verify(studentProfileRepository).incrementEligibleSemesters(any());
    }
}
