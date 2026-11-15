package com.fptu.exe.skillswap.modules.booking.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.booking.service.AvailabilityTemplateService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AvailabilityCreationFlowIntegrationTest {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MentorProfileRepository mentorProfileRepository;

    @Autowired
    private MentorServiceRepository mentorServiceRepository;

    @Autowired
    private MentorAvailabilitySlotRepository slotRepository;

    @Autowired
    private AvailabilityTemplateService availabilityTemplateService;

    @Autowired
    private EntityManager entityManager;

    private User mentorUser;
    private MentorService primaryService;
    private MentorService secondaryService;

    @BeforeEach
    void setUp() {
        mentorUser = userRepository.saveAndFlush(User.builder()
                .email("availability-flow-" + System.nanoTime() + "@test.com")
                .fullName("Availability Flow Mentor")
                .status(UserStatus.ACTIVE)
                .build());

        MentorProfile mentorProfile = mentorProfileRepository.saveAndFlush(MentorProfile.builder()
                .userId(mentorUser.getId())
                .status(MentorStatus.ACTIVE)
                .verifiedAt(LocalDateTime.now().minusDays(1))
                .isAvailable(true)
                .headline("Availability Flow Mentor")
                .expertiseDescription("Testing direct slots and availability templates")
                .foundationSupportLevel(3)
                .outputReviewSupportLevel(3)
                .directionSupportLevel(2)
                .teachingMode(TeachingMode.ONLINE)
                .sessionDuration(60)
                .build());

        primaryService = mentorServiceRepository.saveAndFlush(MentorService.builder()
                .mentorProfile(mentorProfile)
                .title("Primary 60m Session")
                .description("60 minute mentoring")
                .durationMinutes(60)
                .isFree(true)
                .priceScoin(0)
                .isActive(true)
                .build());

        secondaryService = mentorServiceRepository.saveAndFlush(MentorService.builder()
                .mentorProfile(mentorProfile)
                .title("Secondary 60m Session")
                .description("Additional 60 minute mentoring")
                .durationMinutes(60)
                .isFree(true)
                .priceScoin(0)
                .isActive(true)
                .build());
    }

    @Test
    void templateCreation_andDirectSlotReplacementFlow_worksEndToEnd() throws Exception {
        LocalDate targetDate = LocalDate.now(APP_ZONE).plusDays(3);
        String createTemplatePayload = objectMapper.writeValueAsString(Map.of(
                "startTime", "09:00:00",
                "endTime", "11:00:00",
                "weekdays", List.of(targetDate.getDayOfWeek().name()),
                "effectiveFrom", targetDate.toString(),
                "effectiveTo", targetDate.toString(),
                "note", "Weekly template slot",
                "serviceIds", List.of(primaryService.getId())
        ));

        MvcResult templateResult = mockMvc.perform(post("/api/me/availability-templates")
                        .with(mentorAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTemplatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.templateId").isNotEmpty())
                .andExpect(jsonPath("$.data.configVersion").value(1))
                .andReturn();

        UUID templateId = UUID.fromString(objectMapper.readTree(templateResult.getResponse().getContentAsString())
                .path("data").path("templateId").asText());

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/me/availability-slots")
                        .with(mentorAuth())
                        .param("fromDate", targetDate.toString())
                        .param("toDate", targetDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].generatedFromTemplate").value(true))
                .andExpect(jsonPath("$.data[0].templateId").value(templateId.toString()))
                .andExpect(jsonPath("$.data[0].templateVersion").value(1));

        String directOverlapPayload = objectMapper.writeValueAsString(Map.of(
                "startAt", targetDate.atTime(9, 30, 0).toString(),
                "endAt", targetDate.atTime(10, 30, 0).toString(),
                "note", "Manual override without flag",
                "serviceIds", List.of(primaryService.getId())
        ));

        mockMvc.perform(post("/api/me/availability-slots")
                        .with(mentorAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directOverlapPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.GENERATED_OCCURRENCE_REPLACEMENT_REQUIRED.getCode()))
                .andExpect(jsonPath("$.data[0].templateId").value(templateId.toString()))
                .andExpect(jsonPath("$.data[0].occurrenceDate").value(targetDate.toString()))
                .andExpect(jsonPath("$.data[0].currentVersion").value(1));

        String directReplacePayload = objectMapper.writeValueAsString(Map.of(
                "startAt", targetDate.atTime(9, 30, 0).toString(),
                "endAt", targetDate.atTime(10, 30, 0).toString(),
                "note", "Manual override confirmed",
                "serviceIds", List.of(primaryService.getId()),
                "replaceGeneratedOccurrences", true,
                "expectedTemplateVersions", List.of(Map.of(
                        "templateId", templateId.toString(),
                        "expectedVersion", 1
                ))
        ));

        mockMvc.perform(post("/api/me/availability-slots")
                        .with(mentorAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directReplacePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.generatedFromTemplate").value(false))
                .andExpect(jsonPath("$.data.note").value("Manual override confirmed"));

        entityManager.flush();
        entityManager.clear();

        List<MentorAvailabilitySlot> templateSlots = slotRepository.findTemplateOccurrences(templateId, targetDate, targetDate);
        assertEquals(1, templateSlots.size());
        assertFalse(templateSlots.getFirst().isActive());
    }

    @Test
    void directSlotCreationAndUpdate_enforcesDurationAndPreservesRetainedCompositeServiceOnFlush() throws Exception {
        LocalDate targetDate = LocalDate.now(APP_ZONE).plusDays(5);
        String shortPayload = objectMapper.writeValueAsString(Map.of(
                "startAt", targetDate.atTime(14, 0, 0).toString(),
                "endAt", targetDate.atTime(14, 45, 0).toString(),
                "note", "Too short for 60m service",
                "serviceIds", List.of(primaryService.getId())
        ));

        mockMvc.perform(post("/api/me/availability-slots")
                        .with(mentorAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shortPayload))
                .andExpect(status().isBadRequest());

        String createPayload = objectMapper.writeValueAsString(Map.of(
                "startAt", targetDate.atTime(14, 0, 0).toString(),
                "endAt", targetDate.atTime(16, 0, 0).toString(),
                "note", "Initial manual slot",
                "serviceIds", List.of(primaryService.getId())
        ));

        MvcResult createdResult = mockMvc.perform(post("/api/me/availability-slots")
                        .with(mentorAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slotId").isNotEmpty())
                .andExpect(jsonPath("$.data.services.length()").value(1))
                .andReturn();

        JsonNode createdData = objectMapper.readTree(createdResult.getResponse().getContentAsString()).path("data");
        String slotId = createdData.path("slotId").asText();
        int version = createdData.path("version").asInt();

        entityManager.flush();

        String updatePayload = objectMapper.writeValueAsString(Map.of(
                "startAt", targetDate.atTime(14, 30, 0).toString(),
                "endAt", targetDate.atTime(16, 30, 0).toString(),
                "note", "Updated manual slot",
                "serviceIds", List.of(primaryService.getId(), secondaryService.getId()),
                "expectedVersion", version
        ));

        mockMvc.perform(put("/api/me/availability-slots/{slotId}", slotId)
                        .with(mentorAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.note").value("Updated manual slot"))
                .andExpect(jsonPath("$.data.services.length()").value(2));

        entityManager.flush();
        entityManager.clear();

        MentorAvailabilitySlot persisted = slotRepository.findById(UUID.fromString(slotId)).orElseThrow();
        assertNotNull(persisted);
        assertEquals(2, persisted.getSlotServices().size());
        assertTrue(persisted.getSlotServices().stream()
                .map(s -> s.getServiceId())
                .toList()
                .containsAll(List.of(primaryService.getId(), secondaryService.getId())));
    }

    @Test
    void templateCreation_rejectsShortDurationAndUnknownMentorWithoutFkViolation() throws Exception {
        LocalDate targetDate = LocalDate.now(APP_ZONE).plusDays(7);
        String shortTemplatePayload = objectMapper.writeValueAsString(Map.of(
                "startTime", "18:00:00",
                "endTime", "18:45:00",
                "weekdays", List.of(targetDate.getDayOfWeek().name()),
                "effectiveFrom", targetDate.toString(),
                "note", "Too short template",
                "serviceIds", List.of(primaryService.getId())
        ));

        mockMvc.perform(post("/api/me/availability-templates")
                        .with(mentorAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shortTemplatePayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.AVAILABILITY_TEMPLATE_INVALID_SCHEDULE.getCode()));

        UUID missingMentorId = UUID.randomUUID();
        var missingRequest = new com.fptu.exe.skillswap.modules.booking.dto.request.CreateAvailabilityTemplateRequest(
                java.time.LocalTime.of(9, 0),
                java.time.LocalTime.of(11, 0),
                List.of(targetDate.getDayOfWeek()),
                targetDate,
                targetDate,
                "Unknown mentor",
                List.of(primaryService.getId())
        );

        BaseException ex = assertThrows(BaseException.class,
                () -> availabilityTemplateService.create(missingMentorId, missingRequest));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    private RequestPostProcessor mentorAuth() {
        UserPrincipal principal = UserPrincipal.create(mentorUser.getId(), mentorUser.getEmail(), List.of(RoleCode.MENTOR));
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
