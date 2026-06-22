package com.fptu.exe.skillswap.modules.mentor;

import com.fptu.exe.skillswap.modules.academic.domain.AcademicProgram;
import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.academic.repository.AcademicProgramRepository;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminMentorControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MentorProfileRepository mentorProfileRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private AcademicProgramRepository academicProgramRepository;

    private UUID activeMentorId;
    private UUID draftMentorId;

    @BeforeEach
    void setUp() {
        AcademicProgram program = academicProgramRepository.findByCode("CNTT")
                .orElseGet(() -> academicProgramRepository.save(AcademicProgram.builder()
                        .code("CNTT")
                        .nameVi("Công nghệ thông tin")
                        .nameEn("IT")
                        .isActive(true)
                        .build()));

        // 1. Seed Active Mentor
        User user1 = User.builder()
                .email("active.mentor@test.com")
                .fullName("Active Mentor")
                .avatarUrl("active_avatar")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(RoleCode.MENTOR))
                .build();
        user1 = userRepository.save(user1);
        activeMentorId = user1.getId();

        StudentProfile sp1 = StudentProfile.builder()
                .user(user1)
                .program(program)
                .semester(5)
                .intakeYear(2022)
                .build();
        studentProfileRepository.save(sp1);

        MentorProfile mp1 = MentorProfile.builder()
                .user(user1)
                .status(MentorStatus.ACTIVE)
                .isAvailable(true)
                .headline("IT Headline")
                .sessionDuration(60)
                .build();
        mentorProfileRepository.save(mp1);

        // 2. Seed Draft Mentor
        User user2 = User.builder()
                .email("draft.mentor@test.com")
                .fullName("Draft Mentor")
                .avatarUrl("draft_avatar")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(RoleCode.MENTOR))
                .build();
        user2 = userRepository.save(user2);
        draftMentorId = user2.getId();

        MentorProfile mp2 = MentorProfile.builder()
                .user(user2)
                .status(MentorStatus.DRAFT)
                .isAvailable(true)
                .headline("Draft Headline")
                .sessionDuration(60)
                .build();
        mentorProfileRepository.save(mp2);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getMentors_defaultFilter_shouldExcludeDraftAndReturnLightweightFields() throws Exception {
        mockMvc.perform(get("/api/admin/mentors")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Pagination wrapper assertion
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("SUCCESS_0200"))
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.page").exists())
                .andExpect(jsonPath("$.data.size").exists())
                .andExpect(jsonPath("$.data.totalElements").exists())
                .andExpect(jsonPath("$.data.totalPages").exists())
                .andExpect(jsonPath("$.data.last").exists())
                // Verify DRAFT exclusion
                .andExpect(jsonPath("$.data.content[*].email", not(hasItem("draft.mentor@test.com"))))
                .andExpect(jsonPath("$.data.content[*].email", hasItem("active.mentor@test.com")))
                // Verify lightweight fields existence
                .andExpect(jsonPath("$.data.content[0].mentorUserId").exists())
                .andExpect(jsonPath("$.data.content[0].displayName").exists())
                .andExpect(jsonPath("$.data.content[0].email").exists())
                .andExpect(jsonPath("$.data.content[0].avatarUrl").exists())
                .andExpect(jsonPath("$.data.content[0].primaryLabel").value("CNTT"))
                .andExpect(jsonPath("$.data.content[0].completedSessions").exists())
                .andExpect(jsonPath("$.data.content[0].ratingAverage").exists())
                .andExpect(jsonPath("$.data.content[0].mentorStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.content[0].createdAt").exists())
                // Verify detail-only fields absence
                .andExpect(jsonPath("$.data.content[0].userStatus").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].isAvailable").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].bookingSuspendedUntil").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].headline").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].teachingMode").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].sessionDuration").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].reviewCount").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].rejectedBookings").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].lateCancellationPenaltyPoints").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].verifiedAt").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].updatedAt").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getMentors_explicitDraftFilter_shouldReturnDraftMentor() throws Exception {
        mockMvc.perform(get("/api/admin/mentors")
                        .param("status", "DRAFT")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].email", hasItem("draft.mentor@test.com")))
                .andExpect(jsonPath("$.data.content[*].email", not(hasItem("active.mentor@test.com"))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getMentorDetail_existingId_shouldReturnFullDetail() throws Exception {
        mockMvc.perform(get("/api/admin/mentors/{mentorUserId}", activeMentorId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mentorUserId").value(activeMentorId.toString()))
                .andExpect(jsonPath("$.data.email").value("active.mentor@test.com"))
                .andExpect(jsonPath("$.data.displayName").value("Active Mentor"))
                .andExpect(jsonPath("$.data.headline").value("IT Headline"))
                .andExpect(jsonPath("$.data.primaryLabel").value("CNTT"))
                .andExpect(jsonPath("$.data.isAvailable").value(true))
                .andExpect(jsonPath("$.data.sessionDuration").value(60))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.updatedAt").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getMentorDetail_nonExistingId_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/admin/mentors/{mentorUserId}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SYS_0003"));
    }
}
