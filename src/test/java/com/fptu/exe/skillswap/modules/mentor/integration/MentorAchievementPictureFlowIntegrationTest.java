package com.fptu.exe.skillswap.modules.mentor.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.modules.filestorage.dto.request.PublicAssetUploadIntentRequest;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorAchievementPictureConfirmRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorAchievementRequest;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorAchievementRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MentorAchievementPictureFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MentorProfileRepository mentorProfileRepository;

    @Autowired
    private MentorAchievementRepository mentorAchievementRepository;

    @MockBean
    private StorageGateway storageGateway;

    private User mentorUser;
    private UserPrincipal mentorPrincipal;

    @BeforeEach
    void setUp() {
        mentorUser = userRepository.saveAndFlush(User.builder()
                .email("mentor.achievement.test." + UUID.randomUUID() + "@fpt.edu.vn")
                .fullName("Mentor Achievement Test")
                .status(UserStatus.ACTIVE)
                .build());

        MentorProfile profile = MentorProfile.builder()
                .userId(mentorUser.getId())
                .headline("Tech Lead & Mentor")
                .status(MentorStatus.ACTIVE)
                .teachingMode(TeachingMode.HYBRID)
                .build();
        mentorProfileRepository.saveAndFlush(profile);

        mentorPrincipal = UserPrincipal.create(
                mentorUser.getId(),
                mentorUser.getEmail(),
                List.of(RoleCode.MENTOR)
        );

        when(storageGateway.generatePresignedUploadUrl(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> {
                    String filename = inv.getArgument(0);
                    String prefix = inv.getArgument(2);
                    String key = prefix + "/" + UUID.randomUUID() + "_" + filename;
                    return new StorageGateway.PresignedUpload("https://r2.example.com/" + key, "https://cdn.skillswap.asia/" + key, key);
                });

        when(storageGateway.headObject(anyString()))
                .thenAnswer(inv -> new StorageGateway.ObjectMetadata(
                        inv.getArgument(0), "image/png", 1024L * 80, Map.of()
                ));

        when(storageGateway.storageProviderName()).thenReturn("R2");
        when(storageGateway.resolvePublicUrl(anyString()))
                .thenAnswer(inv -> "https://cdn.skillswap.asia/" + inv.getArgument(0));
    }

    @Test
    void endToEnd_AchievementCreation_PresignedIntent_Confirm_And_Remove() throws Exception {
        // 1. Create a new Achievement
        MentorAchievementRequest createReq = new MentorAchievementRequest(
                "AWS Solutions Architect Professional", "Amazon Web Services certificate",
                LocalDate.of(2026, 1, 15), "Cloud Architecture", "Enterprise cloud migration", "https://aws.amazon.com", null
        );

        MvcResult createResult = mockMvc.perform(post("/api/me/mentor-achievements")
                        .with(authentication(auth(mentorPrincipal)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title", is("AWS Solutions Architect Professional")))
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andReturn();

        String achievementIdStr = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();
        UUID achievementId = UUID.fromString(achievementIdStr);

        // 2. Request Upload Intent for Achievement Picture
        PublicAssetUploadIntentRequest intentReq = new PublicAssetUploadIntentRequest("aws_cert.png", "image/png");

        MvcResult intentResult = mockMvc.perform(post("/api/me/mentor-achievements/{achievementId}/picture/upload-intents", achievementId)
                        .with(authentication(auth(mentorPrincipal)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(intentReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.uploadIntentId", notNullValue()))
                .andExpect(jsonPath("$.data.uploadUrl", notNullValue()))
                .andReturn();

        String intentIdStr = objectMapper.readTree(intentResult.getResponse().getContentAsString())
                .path("data").path("uploadIntentId").asText();
        UUID uploadIntentId = UUID.fromString(intentIdStr);

        // 3. Confirm uploaded picture
        MentorAchievementPictureConfirmRequest confirmReq = new MentorAchievementPictureConfirmRequest(uploadIntentId);

        mockMvc.perform(post("/api/me/mentor-achievements/{achievementId}/picture/confirm", achievementId)
                        .with(authentication(auth(mentorPrincipal)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pictureUrl", notNullValue()));

        // 4. Verify listAchievements returns pictureUrl
        mockMvc.perform(get("/api/me/mentor-achievements")
                        .with(authentication(auth(mentorPrincipal))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].pictureUrl", notNullValue()));

        // 5. Remove picture
        mockMvc.perform(delete("/api/me/mentor-achievements/{achievementId}/picture", achievementId)
                        .with(authentication(auth(mentorPrincipal))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pictureUrl").doesNotExist());

        // 6. Delete achievement
        mockMvc.perform(delete("/api/me/mentor-achievements/{achievementId}", achievementId)
                        .with(authentication(auth(mentorPrincipal))))
                .andExpect(status().isOk());

        assertEquals(0, mentorAchievementRepository.findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(mentorUser.getId()).size());
    }

    private UsernamePasswordAuthenticationToken auth(UserPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
