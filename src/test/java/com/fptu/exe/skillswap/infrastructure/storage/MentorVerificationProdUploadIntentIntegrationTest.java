package com.fptu.exe.skillswap.infrastructure.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.security.JwtTokenProvider;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorVerificationDocumentUploadIntentRequest;
import com.fptu.exe.skillswap.modules.mentor.service.MentorVerificationService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "DATABASE_URL=jdbc:h2:mem:testprodverification;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "DATABASE_USERNAME=sa",
        "DATABASE_PASSWORD=",
        "spring.datasource.url=jdbc:h2:mem:testprodverification;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "FLYWAY_ENABLED=false",
        "HIBERNATE_DDL_AUTO=create-drop",
        "application.storage.enabled=true",
        "application.storage.endpoint=https://aff0f4ea8308e09d37a3633c.r2.cloudflarestorage.com",
        "application.storage.access-key=f5f2ce66fdae81d5cc333",
        "application.storage.secret-key=e57a60eaa0bc1f9059c5a8f95fb4a21f9f0f98c827e4e792a5fc",
        "application.storage.bucket=skillswap-prod",
        "application.storage.region=auto",
        "application.storage.public-url-prefix=https://cdn.skillswap.asia",
        "JWT_SECRET_KEY=c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0",
        "JWT_ISSUER=test",
        "JWT_AUDIENCE=test",
        "CORS_ALLOWED_ORIGIN_PATTERNS=http://localhost:3000",
        "CURSOR_AES_KEY=Q3Vyc29yUGhhc2UxQWVzS2V5Rm9yU2tpbGxTd2FwMDE=",
        "CURSOR_HMAC_KEY=Q3Vyc29yUGhhc2UxSG1hY0tleUZvclNraWxsU3dhcDAx"
})
@Transactional
class MentorVerificationProdUploadIntentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MentorVerificationService mentorVerificationService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private String userToken;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .email("test.mentee.prod@fpt.edu.vn")
                .fullName("Nguyen Van Test")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(RoleCode.MENTEE))
                .build());

        mentorVerificationService.requestToBecomeMentor(testUser.getId());
        userToken = jwtTokenProvider.generateAccessToken(testUser.getId(), testUser.getEmail(), List.of("MENTEE"));
    }

    @Test
    @DisplayName("Gửi request upload intent trong môi trường Prod với cấu hình R2 - Phải trả về 201 kèm uploadUrl R2 thành công")
    void testCreateUploadIntentInProdProfileReturns201AndR2PresignedUrl() throws Exception {
        MentorVerificationDocumentUploadIntentRequest request = new MentorVerificationDocumentUploadIntentRequest(
                "ConfirmationLetter_NhatTT.jpg",
                "image/jpeg",
                150937L
        );

        mockMvc.perform(post("/api/me/mentor-verification/documents/upload-intents")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.uploadIntentId").exists())
                .andExpect(jsonPath("$.data.uploadUrl", notNullValue()))
                .andExpect(jsonPath("$.data.uploadUrl", containsString("r2.cloudflarestorage.com")))
                .andExpect(jsonPath("$.data.uploadUrl", containsString("skillswap-prod")))
                .andExpect(jsonPath("$.data.requiredHeaders['Content-Type']").value("image/jpeg"))
                .andExpect(jsonPath("$.data.status").value("PENDING_UPLOAD"));
    }
}
