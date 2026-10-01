package com.fptu.exe.skillswap.modules.identity.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.identity.domain.DataDeletionRequest;
import com.fptu.exe.skillswap.modules.identity.domain.DataDeletionRequestStatus;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserSession;
import com.fptu.exe.skillswap.modules.identity.domain.UserSessionState;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.dto.request.DataDeletionPublicRequest;
import com.fptu.exe.skillswap.modules.identity.repository.DataDeletionRequestRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserSessionRepository;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountDeletionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private DataDeletionRequestRepository dataDeletionRequestRepository;

    private User testUser;
    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        testUser = userRepository.saveAndFlush(User.builder()
                .email("test.deletion." + UUID.randomUUID() + "@fpt.edu.vn")
                .fullName("Nguyen Van A")
                .avatarUrl("https://example.com/avatar.jpg")
                .status(UserStatus.ACTIVE)
                .roles(new java.util.HashSet<>(Set.of(RoleCode.MENTEE)))
                .build());

        userPrincipal = UserPrincipal.create(
                testUser.getId(),
                testUser.getEmail(),
                List.of(RoleCode.MENTEE)
        );

        UserSession session = UserSession.builder()
                .user(testUser)
                .refreshTokenHash("hash-" + UUID.randomUUID())
                .expiresAt(LocalDateTime.now().plusHours(2))
                .isRevoked(false)
                .sessionState(UserSessionState.ACTIVE)
                .build();
        userSessionRepository.saveAndFlush(session);
    }

    @Test
    void deleteMyAccount_shouldSucceed_andAnonymizePii_andRevokeSessions() throws Exception {
        mockMvc.perform(delete("/api/me")
                        .with(authentication(auth(userPrincipal))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.data", containsString("thành công")));

        // Verify in database (use findByIdIncludingDeleted since @SQLRestriction applies to normal queries)
        User deletedUser = userRepository.findByEmailIncludingDeleted("deleted." + testUser.getId() + "@deleted.skillswap.local")
                .orElseThrow();

        assertEquals(UserStatus.DELETED, deletedUser.getStatus());
        assertEquals("Người dùng đã xóa", deletedUser.getFullName());
        assertNull(deletedUser.getAvatarUrl());
        assertNotNull(deletedUser.getDeletedAt());

        // Verify sessions revoked
        List<UserSession> activeSessions = userSessionRepository.findByUserIdAndIsRevokedFalse(testUser.getId());
        assertTrue(activeSessions.isEmpty());
    }

    @Test
    void accessStaticDataDeletionPage_shouldReturnHtml_withoutAuth() throws Exception {
        mockMvc.perform(get("/data-deletion.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("FU-SkillSwap")))
                .andExpect(content().string(containsString("Google Play Data Safety")));
    }

    @Test
    void submitPublicDataDeletionRequest_shouldSucceed_andSaveToDatabase() throws Exception {
        DataDeletionPublicRequest request = new DataDeletionPublicRequest(
                "student.test@fpt.edu.vn",
                "Tôi đã tốt nghiệp và muốn xóa dữ liệu tài khoản."
        );

        mockMvc.perform(post("/api/public/data-deletion-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.data", containsString("ghi nhận")));

        List<DataDeletionRequest> requests = dataDeletionRequestRepository.findByEmailOrderByCreatedAtDesc("student.test@fpt.edu.vn");
        assertFalse(requests.isEmpty());
        assertEquals(DataDeletionRequestStatus.PENDING, requests.getFirst().getStatus());
        assertEquals("Tôi đã tốt nghiệp và muốn xóa dữ liệu tài khoản.", requests.getFirst().getReason());
    }

    private UsernamePasswordAuthenticationToken auth(UserPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
