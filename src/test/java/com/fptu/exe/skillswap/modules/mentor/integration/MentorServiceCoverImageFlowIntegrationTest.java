package com.fptu.exe.skillswap.modules.mentor.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.modules.filestorage.dto.request.PublicAssetUploadIntentRequest;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceDeliveryMode;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.dto.request.CreateMentorServiceRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.request.UpdateMentorServiceRequest;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.service.MentorProfileService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MentorServiceCoverImageFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MentorProfileRepository mentorProfileRepository;

    @MockBean
    private MentorProfileService mentorProfileService;

    @MockBean
    private StorageGateway storageGateway;

    private User mentorUser;
    private UserPrincipal mentorPrincipal;

    @BeforeEach
    void setUp() {
        mentorUser = userRepository.saveAndFlush(User.builder()
                .email("mentor.service.test." + UUID.randomUUID() + "@fpt.edu.vn")
                .fullName("Mentor Service Test")
                .status(UserStatus.ACTIVE)
                .build());

        MentorProfile profile = MentorProfile.builder()
                .userId(mentorUser.getId())
                .headline("Software Engineer")
                .expertiseDescription("Backend Spring Boot")
                .status(MentorStatus.ACTIVE)
                .verifiedAt(LocalDateTime.now().minusDays(1))
                .teachingMode(TeachingMode.HYBRID)
                .build();
        mentorProfileRepository.saveAndFlush(profile);

        mentorPrincipal = UserPrincipal.create(
                mentorUser.getId(),
                mentorUser.getEmail(),
                List.of(RoleCode.MENTOR)
        );

        when(mentorProfileService.hasCompletedMentorProfile(any())).thenReturn(true);

        when(storageGateway.generatePresignedUploadUrl(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> {
                    String filename = inv.getArgument(0);
                    String prefix = inv.getArgument(2);
                    String key = prefix + "/" + UUID.randomUUID() + "_" + filename;
                    return new StorageGateway.PresignedUpload("https://r2.example.com/" + key, "https://cdn.skillswap.asia/" + key, key);
                });

        when(storageGateway.headObject(anyString()))
                .thenAnswer(inv -> new StorageGateway.ObjectMetadata(
                        inv.getArgument(0), "image/png", 1024L * 100, Map.of()
                ));

        when(storageGateway.storageProviderName()).thenReturn("R2");
        when(storageGateway.resolvePublicUrl(anyString()))
                .thenAnswer(inv -> "https://cdn.skillswap.asia/" + inv.getArgument(0));
    }

    @Test
    void endToEnd_ServiceCreationWithCoverImage_And_Removal() throws Exception {
        // 1. Request Upload Intent for Service Cover Image
        PublicAssetUploadIntentRequest intentReq = new PublicAssetUploadIntentRequest("service-cover.png", "image/png");

        MvcResult intentResult = mockMvc.perform(post("/api/me/mentor-services/cover-image/upload-intents")
                        .with(authentication(auth(mentorPrincipal)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(intentReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(201)))
                .andExpect(jsonPath("$.data.uploadIntentId", notNullValue()))
                .andExpect(jsonPath("$.data.uploadUrl", notNullValue()))
                .andReturn();

        String intentIdStr = objectMapper.readTree(intentResult.getResponse().getContentAsString())
                .path("data").path("uploadIntentId").asText();
        UUID intentId = UUID.fromString(intentIdStr);

        // 2. Confirm Upload
        MvcResult confirmResult = mockMvc.perform(post("/api/me/mentor-services/cover-image/{intentId}/confirm", intentId)
                        .with(authentication(auth(mentorPrincipal))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetId", notNullValue()))
                .andExpect(jsonPath("$.data.publicUrl", notNullValue()))
                .andReturn();

        String assetIdStr = objectMapper.readTree(confirmResult.getResponse().getContentAsString())
                .path("data").path("assetId").asText();
        UUID assetId = UUID.fromString(assetIdStr);

        // 3. Create Service with coverAssetId
        CreateMentorServiceRequest createReq = new CreateMentorServiceRequest(
                "Java Spring Boot Mentoring",
                "Huong dan xay dung REST API chuan enterprise",
                "Hieu kien truc va lam chu framework",
                60,
                false,
                72_000,
                true,
                MentorServiceDeliveryMode.ONE_TO_ONE,
                assetId
        );

        MvcResult createResult = mockMvc.perform(post("/api/me/mentor-services")
                        .with(authentication(auth(mentorPrincipal)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title", is("Java Spring Boot Mentoring")))
                .andExpect(jsonPath("$.data.coverAssetId", is(assetId.toString())))
                .andExpect(jsonPath("$.data.coverImageUrl", notNullValue()))
                .andReturn();

        String serviceIdStr = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("serviceId").asText();
        UUID serviceId = UUID.fromString(serviceIdStr);
        int version = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("version").asInt();

        // 4. Get Service Detail
        mockMvc.perform(get("/api/me/mentor-services/{serviceId}", serviceId)
                        .with(authentication(auth(mentorPrincipal))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.serviceId", is(serviceId.toString())))
                .andExpect(jsonPath("$.data.coverAssetId", is(assetId.toString())))
                .andExpect(jsonPath("$.data.coverImageUrl", notNullValue()));

        // 5. Remove Cover Image via DELETE endpoint
        mockMvc.perform(delete("/api/me/mentor-services/{serviceId}/cover-image", serviceId)
                        .with(authentication(auth(mentorPrincipal))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverAssetId", nullValue()))
                .andExpect(jsonPath("$.data.coverImageUrl", nullValue()));

        // 6. Update Service with removeCoverImage = false and re-assign cover
        UpdateMentorServiceRequest updateReq = new UpdateMentorServiceRequest(
                "Java Spring Boot Mentoring - Updated",
                "Huong dan xay dung REST API chuan enterprise",
                "Hieu kien truc va lam chu framework",
                false,
                72_000,
                true,
                version + 1,
                assetId,
                false
        );

        mockMvc.perform(put("/api/me/mentor-services/{serviceId}", serviceId)
                        .with(authentication(auth(mentorPrincipal)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverAssetId", is(assetId.toString())))
                .andExpect(jsonPath("$.data.coverImageUrl", notNullValue()));
    }

    private UsernamePasswordAuthenticationToken auth(UserPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
