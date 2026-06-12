package com.fptu.exe.skillswap.modules.academic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.academic.dto.StudentProfileRequest;
import com.fptu.exe.skillswap.modules.academic.dto.StudentProfileResponse;
import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StudentProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AcademicService academicService;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID userId;
    private UserPrincipal userPrincipal;
    private StudentProfileResponse mockResponse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        userPrincipal = UserPrincipal.create(userId, "test@fpt.edu.vn", List.of(RoleCode.MENTEE));
        mockResponse = StudentProfileResponse.builder()
                .userId(userId)
                .email("test@fpt.edu.vn")
                .studentCode("HE123456")
                .displayName("Test User")
                .semester(4)
                .intakeYear(2023)
                .isAlumni(false)
                .build();
    }

    @Test
    void getStudentProfile_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/me/student-profile")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getStudentProfile_authenticated_shouldReturnProfile() throws Exception {
        when(academicService.getStudentProfile(userId)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/me/student-profile")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_0200"))
                .andExpect(jsonPath("$.data.studentCode").value("HE123456"))
                .andExpect(jsonPath("$.data.email").value("test@fpt.edu.vn"));
    }

    @Test
    void getStudentProfile_notFound_shouldReturn404() throws Exception {
        when(academicService.getStudentProfile(userId)).thenThrow(new ResourceNotFoundException("Hồ sơ học thuật chưa được tạo"));

        mockMvc.perform(get("/api/me/student-profile")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Hồ sơ học thuật chưa được tạo"));
    }

    @Test
    void updateStudentProfile_unauthenticated_shouldReturn401() throws Exception {
        StudentProfileRequest request = StudentProfileRequest.builder()
                .studentCode("HE123456")
                .build();

        mockMvc.perform(put("/api/me/student-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateStudentProfile_invalidInput_shouldReturn400() throws Exception {
        // Leave studentCode null to trigger NotBlank validation
        StudentProfileRequest request = StudentProfileRequest.builder()
                .studentCode(null)
                .campusId(UUID.randomUUID())
                .programId(UUID.randomUUID())
                .specializationId(UUID.randomUUID())
                .semester(1)
                .intakeYear(2023)
                .isAlumni(false)
                .build();

        mockMvc.perform(put("/api/me/student-profile")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAL_3001"))
                .andExpect(jsonPath("$.message").value("studentCode: Mã số sinh viên không được để trống"));
    }

    @Test
    void updateStudentProfile_invalidStudentCodeFormat_shouldReturn400() throws Exception {
        StudentProfileRequest request = StudentProfileRequest.builder()
                .studentCode("XY192621") // XY is invalid, only H,S,D,Q,C and E,S,A allowed
                .campusId(UUID.randomUUID())
                .programId(UUID.randomUUID())
                .specializationId(UUID.randomUUID())
                .semester(1)
                .intakeYear(2023)
                .isAlumni(false)
                .build();

        mockMvc.perform(put("/api/me/student-profile")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAL_3001"))
                .andExpect(jsonPath("$.message").value("studentCode: Mã số sinh viên không đúng định dạng (Ví dụ: SE192621)"));
    }

    @Test
    void updateStudentProfile_validInput_shouldReturn200() throws Exception {
        UUID campusId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        UUID specId = UUID.randomUUID();

        StudentProfileRequest request = StudentProfileRequest.builder()
                .studentCode("HE123456")
                .campusId(campusId)
                .programId(programId)
                .specializationId(specId)
                .semester(4)
                .intakeYear(2023)
                .isAlumni(false)
                .displayName("New Display Name")
                .avatarUrl("http://new.avatar.url")
                .bio("New bio")
                .build();

        when(academicService.updateStudentProfile(eq(userId), any(StudentProfileRequest.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(put("/api/me/student-profile")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_0200"))
                .andExpect(jsonPath("$.data.studentCode").value("HE123456"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Validation: Các trường @NotNull bắt buộc còn lại
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void updateStudentProfile_nullCampusId_shouldReturn400() throws Exception {
        StudentProfileRequest request = StudentProfileRequest.builder()
                .studentCode("SE192621")
                .campusId(null)           // thiếu campusId
                .programId(UUID.randomUUID())
                .specializationId(UUID.randomUUID())
                .semester(1)
                .intakeYear(2023)
                .isAlumni(false)
                .build();

        mockMvc.perform(put("/api/me/student-profile")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAL_3001"))
                .andExpect(jsonPath("$.message").value("campusId: Cơ sở không được để trống"));
    }

    @Test
    void updateStudentProfile_nullProgramId_shouldReturn400() throws Exception {
        StudentProfileRequest request = StudentProfileRequest.builder()
                .studentCode("SE192621")
                .campusId(UUID.randomUUID())
                .programId(null)          // thiếu programId
                .specializationId(UUID.randomUUID())
                .semester(1)
                .intakeYear(2023)
                .isAlumni(false)
                .build();

        mockMvc.perform(put("/api/me/student-profile")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAL_3001"))
                .andExpect(jsonPath("$.message").value("programId: Ngành học không được để trống"));
    }

    @Test
    void updateStudentProfile_nullSpecializationId_shouldReturn400() throws Exception {
        StudentProfileRequest request = StudentProfileRequest.builder()
                .studentCode("SE192621")
                .campusId(UUID.randomUUID())
                .programId(UUID.randomUUID())
                .specializationId(null)   // thiếu specializationId
                .semester(1)
                .intakeYear(2023)
                .isAlumni(false)
                .build();

        mockMvc.perform(put("/api/me/student-profile")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAL_3001"))
                .andExpect(jsonPath("$.message").value("specializationId: Chuyên ngành không được để trống"));
    }

    @Test
    void updateStudentProfile_nullSemester_shouldReturn400() throws Exception {
        StudentProfileRequest request = StudentProfileRequest.builder()
                .studentCode("SE192621")
                .campusId(UUID.randomUUID())
                .programId(UUID.randomUUID())
                .specializationId(UUID.randomUUID())
                .semester(null)           // thiếu semester
                .intakeYear(2023)
                .isAlumni(false)
                .build();

        mockMvc.perform(put("/api/me/student-profile")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAL_3001"))
                .andExpect(jsonPath("$.message").value("semester: Học kỳ không được để trống"));
    }

    @Test
    void updateStudentProfile_nullIntakeYear_shouldReturn400() throws Exception {
        StudentProfileRequest request = StudentProfileRequest.builder()
                .studentCode("SE192621")
                .campusId(UUID.randomUUID())
                .programId(UUID.randomUUID())
                .specializationId(UUID.randomUUID())
                .semester(3)
                .intakeYear(null)         // thiếu intakeYear
                .isAlumni(false)
                .build();

        mockMvc.perform(put("/api/me/student-profile")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAL_3001"))
                .andExpect(jsonPath("$.message").value("intakeYear: Khóa tuyển sinh không được để trống"));
    }

    @Test
    void updateStudentProfile_nullIsAlumni_shouldReturn400() throws Exception {
        StudentProfileRequest request = StudentProfileRequest.builder()
                .studentCode("SE192621")
                .campusId(UUID.randomUUID())
                .programId(UUID.randomUUID())
                .specializationId(UUID.randomUUID())
                .semester(3)
                .intakeYear(2019)
                .isAlumni(null)           // thiếu isAlumni
                .build();

        mockMvc.perform(put("/api/me/student-profile")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAL_3001"))
                .andExpect(jsonPath("$.message").value("isAlumni: Trạng thái cựu sinh viên không được để trống"));
    }

    @Test
    void updateStudentProfile_displayNameTooLong_shouldReturn400() throws Exception {
        // displayName vượt quá 150 ký tự
        String longName = "A".repeat(151);

        StudentProfileRequest request = StudentProfileRequest.builder()
                .studentCode("SE192621")
                .campusId(UUID.randomUUID())
                .programId(UUID.randomUUID())
                .specializationId(UUID.randomUUID())
                .semester(3)
                .intakeYear(2019)
                .isAlumni(false)
                .displayName(longName)
                .build();

        mockMvc.perform(put("/api/me/student-profile")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAL_3001"))
                .andExpect(jsonPath("$.message").value("displayName: Tên hiển thị không được quá 150 ký tự"));
    }
}
