package com.fptu.exe.skillswap.modules.academic;

import com.fptu.exe.skillswap.modules.academic.domain.AcademicProgram;
import com.fptu.exe.skillswap.modules.academic.domain.Campus;
import com.fptu.exe.skillswap.modules.academic.domain.CampusCode;
import com.fptu.exe.skillswap.modules.academic.domain.Specialization;
import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.academic.dto.AcademicProgramResponse;
import com.fptu.exe.skillswap.modules.academic.dto.CampusResponse;
import com.fptu.exe.skillswap.modules.academic.dto.SpecializationResponse;
import com.fptu.exe.skillswap.modules.academic.dto.StudentProfileRequest;
import com.fptu.exe.skillswap.modules.academic.dto.StudentProfileResponse;
import com.fptu.exe.skillswap.modules.academic.repository.AcademicProgramRepository;
import com.fptu.exe.skillswap.modules.academic.repository.CampusRepository;
import com.fptu.exe.skillswap.modules.academic.repository.SpecializationRepository;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
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

    @Test
    void getAllCampuses_shouldReturnActiveCampusesOnly() {
        Campus activeCampus = Campus.builder()
                .id(UUID.randomUUID())
                .code(CampusCode.HA_NOI)
                .name("Hanoi Campus")
                .city("Hanoi")
                .isActive(true)
                .build();

        when(campusRepository.findByIsActiveTrue()).thenReturn(List.of(activeCampus));

        List<CampusResponse> result = academicService.getAllCampuses();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Hanoi Campus", result.getFirst().getName());
        verify(campusRepository, times(1)).findByIsActiveTrue();
    }

    @Test
    void getAllAcademicPrograms_shouldReturnActiveProgramsOnly() {
        AcademicProgram activeProgram = AcademicProgram.builder()
                .id(UUID.randomUUID())
                .code("CNTT")
                .nameVi("Công nghệ thông tin")
                .nameEn("Information Technology")
                .isActive(true)
                .build();

        when(academicProgramRepository.findByIsActiveTrue()).thenReturn(List.of(activeProgram));

        List<AcademicProgramResponse> result = academicService.getAllAcademicPrograms();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("CNTT", result.getFirst().getCode());
        verify(academicProgramRepository, times(1)).findByIsActiveTrue();
    }

    @Test
    void getAllSpecializations_shouldReturnActiveSpecializationsOnly() {
        AcademicProgram program = AcademicProgram.builder().id(UUID.randomUUID()).build();
        Specialization activeSpec = Specialization.builder()
                .id(UUID.randomUUID())
                .program(program)
                .code("CNTT_KTPM")
                .nameVi("Kỹ thuật phần mềm")
                .nameEn("Software Engineering")
                .isActive(true)
                .build();

        when(specializationRepository.findByIsActiveTrue()).thenReturn(List.of(activeSpec));

        List<SpecializationResponse> result = academicService.getAllSpecializations();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("CNTT_KTPM", result.getFirst().getCode());
        verify(specializationRepository, times(1)).findByIsActiveTrue();
    }

    @Test
    void getSpecializationsByProgram_shouldThrowNotFound_whenProgramDoesNotExist() {
        UUID programId = UUID.randomUUID();
        when(academicProgramRepository.existsById(programId)).thenReturn(false);

        BaseException ex = assertThrows(BaseException.class, () -> academicService.getSpecializationsByProgram(programId));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        assertEquals("Không tìm thấy ngành học liên kết", ex.getMessage());
    }

    @Test
    void getSpecializationsByProgram_shouldReturnSpecs_whenProgramExists() {
        UUID programId = UUID.randomUUID();
        AcademicProgram program = AcademicProgram.builder().id(programId).build();
        Specialization spec = Specialization.builder()
                .id(UUID.randomUUID())
                .program(program)
                .code("CNTT_KTPM")
                .isActive(true)
                .build();

        when(academicProgramRepository.existsById(programId)).thenReturn(true);
        when(specializationRepository.findByProgramIdAndIsActiveTrue(programId)).thenReturn(List.of(spec));

        List<SpecializationResponse> result = academicService.getSpecializationsByProgram(programId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("CNTT_KTPM", result.getFirst().getCode());
    }

    @Test
    void getStudentProfile_shouldThrowResourceNotFoundException_whenProfileDoesNotExist() {
        UUID userId = UUID.randomUUID();
        when(studentProfileRepository.findById(userId)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> academicService.getStudentProfile(userId));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        assertEquals("Hồ sơ học thuật chưa được tạo", ex.getMessage());
    }

    @Test
    void getStudentProfile_shouldReturnResponse_whenProfileExists() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("test@fpt.edu.vn")
                .fullName("Test User")
                .avatarUrl("http://avatar.url")
                .build();

        Campus campus = Campus.builder().id(UUID.randomUUID()).build();
        AcademicProgram program = AcademicProgram.builder().id(UUID.randomUUID()).build();
        Specialization spec = Specialization.builder().id(UUID.randomUUID()).program(program).code("CNTT_KTPM").build();

        StudentProfile profile = StudentProfile.builder()
                .userId(userId)
                .user(user)
                .studentCode("HE123456")
                .campus(campus)
                .program(program)
                .specialization(spec)
                .semester(4)
                .intakeYear(2023)
                .isAlumni(false)
                .bio("My bio")
                .build();

        when(studentProfileRepository.findById(userId)).thenReturn(Optional.of(profile));

        StudentProfileResponse response = academicService.getStudentProfile(userId);

        assertNotNull(response);
        assertEquals("HE123456", response.getStudentCode());
        assertEquals("Test User", response.getDisplayName());
        assertEquals("test@fpt.edu.vn", response.getEmail());
    }

    @Test
    void updateStudentProfile_shouldThrowResourceNotFoundException_whenUserNotFound() {
        UUID userId = UUID.randomUUID();
        StudentProfileRequest request = StudentProfileRequest.builder().build();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> academicService.updateStudentProfile(userId, request));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        assertEquals("Không tìm thấy người dùng", ex.getMessage());
    }

    @Test
    void updateStudentProfile_shouldThrowBaseException_whenCampusNotFound() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        UUID campusId = UUID.randomUUID();
        StudentProfileRequest request = StudentProfileRequest.builder()
                .campusId(campusId)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(campusRepository.findById(campusId)).thenReturn(Optional.empty());

        BaseException ex = assertThrows(BaseException.class, () -> academicService.updateStudentProfile(userId, request));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        assertEquals("Không tìm thấy cơ sở học tập", ex.getMessage());
    }

    @Test
    void updateStudentProfile_shouldThrowBaseException_whenProgramNotFound() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        UUID campusId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        StudentProfileRequest request = StudentProfileRequest.builder()
                .campusId(campusId)
                .programId(programId)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(campusRepository.findById(campusId)).thenReturn(Optional.of(new Campus()));
        when(academicProgramRepository.findById(programId)).thenReturn(Optional.empty());

        BaseException ex = assertThrows(BaseException.class, () -> academicService.updateStudentProfile(userId, request));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        assertEquals("Không tìm thấy ngành học", ex.getMessage());
    }

    @Test
    void updateStudentProfile_shouldThrowBaseException_whenSpecializationNotFound() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        UUID campusId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        UUID specId = UUID.randomUUID();
        StudentProfileRequest request = StudentProfileRequest.builder()
                .campusId(campusId)
                .programId(programId)
                .specializationId(specId)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(campusRepository.findById(campusId)).thenReturn(Optional.of(new Campus()));
        when(academicProgramRepository.findById(programId)).thenReturn(Optional.of(new AcademicProgram()));
        when(specializationRepository.findById(specId)).thenReturn(Optional.empty());

        BaseException ex = assertThrows(BaseException.class, () -> academicService.updateStudentProfile(userId, request));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        assertEquals("Không tìm thấy chuyên ngành", ex.getMessage());
    }

    @Test
    void updateStudentProfile_shouldThrowBaseException_whenSpecializationDoesNotBelongToProgram() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        UUID campusId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        UUID specId = UUID.randomUUID();
        StudentProfileRequest request = StudentProfileRequest.builder()
                .campusId(campusId)
                .programId(programId)
                .specializationId(specId)
                .build();

        AcademicProgram selectedProgram = AcademicProgram.builder().id(programId).build();
        AcademicProgram differentProgram = AcademicProgram.builder().id(UUID.randomUUID()).build();
        Specialization spec = Specialization.builder().id(specId).program(differentProgram).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(campusRepository.findById(campusId)).thenReturn(Optional.of(new Campus()));
        when(academicProgramRepository.findById(programId)).thenReturn(Optional.of(selectedProgram));
        when(specializationRepository.findById(specId)).thenReturn(Optional.of(spec));

        BaseException ex = assertThrows(BaseException.class, () -> academicService.updateStudentProfile(userId, request));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertEquals("Chuyên ngành không thuộc ngành học đã chọn", ex.getMessage());
    }

    @Test
    void updateStudentProfile_shouldThrowBaseException_whenStudentCodeAlreadyExists() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        UUID campusId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        UUID specId = UUID.randomUUID();
        StudentProfileRequest request = StudentProfileRequest.builder()
                .campusId(campusId)
                .programId(programId)
                .specializationId(specId)
                .studentCode("HE123456")
                .build();

        AcademicProgram program = AcademicProgram.builder().id(programId).build();
        Specialization spec = Specialization.builder().id(specId).program(program).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(campusRepository.findById(campusId)).thenReturn(Optional.of(new Campus()));
        when(academicProgramRepository.findById(programId)).thenReturn(Optional.of(program));
        when(specializationRepository.findById(specId)).thenReturn(Optional.of(spec));
        when(studentProfileRepository.existsByStudentCodeAndUserIdNot("HE123456", userId)).thenReturn(true);

        BaseException ex = assertThrows(BaseException.class, () -> academicService.updateStudentProfile(userId, request));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertEquals("Mã số sinh viên đã tồn tại trên hệ thống", ex.getMessage());
    }

    @Test
    void updateStudentProfile_shouldCreateAndSaveProfile_whenDoesNotExist() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("test@fpt.edu.vn")
                .fullName("Original Name")
                .avatarUrl("http://original.avatar")
                .build();

        UUID campusId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        UUID specId = UUID.randomUUID();
        StudentProfileRequest request = StudentProfileRequest.builder()
                .campusId(campusId)
                .programId(programId)
                .specializationId(specId)
                .studentCode("  HE123456  ")
                .displayName("Updated Name")
                .avatarUrl("http://new.avatar")
                .semester(3)
                .intakeYear(2022)
                .isAlumni(false)
                .bio("New Bio")
                .build();

        Campus campus = Campus.builder().id(campusId).build();
        AcademicProgram program = AcademicProgram.builder().id(programId).build();
        Specialization spec = Specialization.builder().id(specId).program(program).code("CNTT_KTPM").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(campusRepository.findById(campusId)).thenReturn(Optional.of(campus));
        when(academicProgramRepository.findById(programId)).thenReturn(Optional.of(program));
        when(specializationRepository.findById(specId)).thenReturn(Optional.of(spec));
        when(studentProfileRepository.existsByStudentCodeAndUserIdNot("HE123456", userId)).thenReturn(false);
        when(studentProfileRepository.findById(userId)).thenReturn(Optional.empty());
        when(studentProfileRepository.save(any(StudentProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudentProfileResponse response = academicService.updateStudentProfile(userId, request);

        assertNotNull(response);
        assertEquals("HE123456", response.getStudentCode());
        assertEquals("Updated Name", response.getDisplayName());
        assertEquals("http://new.avatar", response.getAvatarUrl());
        assertEquals("New Bio", response.getBio());
        assertEquals("Updated Name", user.getFullName());
        assertEquals("http://new.avatar", user.getAvatarUrl());

        verify(userRepository, times(1)).save(user);
        verify(studentProfileRepository, times(1)).save(any(StudentProfile.class));
    }
}
