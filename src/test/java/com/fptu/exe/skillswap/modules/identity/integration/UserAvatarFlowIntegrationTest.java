package com.fptu.exe.skillswap.modules.identity.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.modules.filestorage.port.PublicAssetUploadPort;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.dto.request.UserAvatarConfirmRequest;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserAvatarFlowIntegrationTest {

    private static final String MAIL_AVATAR = "https://lh3.googleusercontent.com/initial-mail-avatar";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private StorageGateway storageGateway;

    private User menteeUser;
    private User otherUser;
    private UserPrincipal menteePrincipal;
    private UserPrincipal otherPrincipal;

    @BeforeEach
    void setUp() {
        menteeUser = userRepository.saveAndFlush(User.builder()
                .email("avatar.mentee." + UUID.randomUUID() + "@fpt.edu.vn")
                .fullName("Avatar Mentee")
                .avatarUrl(MAIL_AVATAR)
                .oauthAvatarUrl(MAIL_AVATAR)
                .status(UserStatus.ACTIVE)
                .roles(new HashSet<>(Set.of(RoleCode.MENTEE)))
                .build());
        menteePrincipal = UserPrincipal.create(menteeUser.getId(), menteeUser.getEmail(), List.of(RoleCode.MENTEE));

        otherUser = userRepository.saveAndFlush(User.builder()
                .email("avatar.other." + UUID.randomUUID() + "@fpt.edu.vn")
                .fullName("Other User")
                .status(UserStatus.ACTIVE)
                .roles(new HashSet<>(Set.of(RoleCode.MENTEE)))
                .build());
        otherPrincipal = UserPrincipal.create(otherUser.getId(), otherUser.getEmail(), List.of(RoleCode.MENTEE));

        when(storageGateway.generatePresignedUploadUrl(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> {
                    String filename = inv.getArgument(0);
                    String prefix = inv.getArgument(2);
                    String key = prefix + "/" + UUID.randomUUID() + "_" + filename;
                    return new StorageGateway.PresignedUpload("https://r2.example.com/" + key, "https://cdn.skillswap.asia/" + key, key);
                });
        when(storageGateway.headObject(anyString()))
                .thenAnswer(inv -> new StorageGateway.ObjectMetadata(
                        inv.getArgument(0), "image/png", 1024L * 256, Map.of()));
        when(storageGateway.storageProviderName()).thenReturn("R2");
        when(storageGateway.resolvePublicUrl(anyString()))
                .thenAnswer(inv -> "https://cdn.skillswap.asia/" + inv.getArgument(0));
    }

    @Test
    void unauthenticated_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/me/avatar/upload-intents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PublicAssetUploadPort.UploadRequest("avatar.png", "image/png"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endToEnd_uploadConfirmAndRemove_shouldUpdateAndRestoreMailAvatar() throws Exception {
        // 1. Create upload intent
        PublicAssetUploadPort.UploadRequest intentReq = new PublicAssetUploadPort.UploadRequest("my-avatar.png", "image/png");
        MvcResult intentResult = mockMvc.perform(post("/api/me/avatar/upload-intents")
                        .with(authentication(auth(menteePrincipal)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(intentReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is(201)))
                .andExpect(jsonPath("$.data.uploadIntentId", notNullValue()))
                .andExpect(jsonPath("$.data.uploadUrl", notNullValue()))
                .andReturn();

        UUID intentId = UUID.fromString(objectMapper.readTree(intentResult.getResponse().getContentAsString())
                .path("data").path("uploadIntentId").asText());

        // 2. Another user cannot confirm this intent
        mockMvc.perform(post("/api/me/avatar/confirm")
                        .with(authentication(auth(otherPrincipal)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserAvatarConfirmRequest(intentId))))
                .andExpect(status().isNotFound());

        // 3. Owner confirms avatar upload -> avatarUrl updated
        MvcResult confirmResult = mockMvc.perform(post("/api/me/avatar/confirm")
                        .with(authentication(auth(menteePrincipal)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserAvatarConfirmRequest(intentId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId", is(menteeUser.getId().toString())))
                .andExpect(jsonPath("$.data.avatarAssetId", notNullValue()))
                .andExpect(jsonPath("$.data.avatarUrl", notNullValue()))
                .andExpect(jsonPath("$.data.restoredFromMail", is(false)))
                .andReturn();

        String customAvatarUrl = objectMapper.readTree(confirmResult.getResponse().getContentAsString())
                .path("data").path("avatarUrl").asText();

        // 4. GET /api/auth/me reflects custom avatar immediately
        mockMvc.perform(get("/api/auth/me")
                        .with(authentication(auth(menteePrincipal))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl", is(customAvatarUrl)));

        // 5. Remove avatar -> restores original mail/Google avatar
        mockMvc.perform(delete("/api/me/avatar")
                        .with(authentication(auth(menteePrincipal))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarAssetId", nullValue()))
                .andExpect(jsonPath("$.data.avatarUrl", is(MAIL_AVATAR)))
                .andExpect(jsonPath("$.data.restoredFromMail", is(true)));

        // 6. GET /api/auth/me reflects restored mail avatar
        mockMvc.perform(get("/api/auth/me")
                        .with(authentication(auth(menteePrincipal))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl", is(MAIL_AVATAR)));
    }

    private UsernamePasswordAuthenticationToken auth(UserPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
