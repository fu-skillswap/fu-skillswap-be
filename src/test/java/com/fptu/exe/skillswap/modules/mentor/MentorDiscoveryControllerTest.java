package com.fptu.exe.skillswap.modules.mentor;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.feedback.dto.MentorReviewResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorAvailabilitySlotResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorDiscoveryCardResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorDiscoveryDetailResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorRecommendationResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorTagResponse;
import com.fptu.exe.skillswap.modules.mentor.service.MentorDiscoveryService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MentorDiscoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MentorDiscoveryService mentorDiscoveryService;

    private UUID userId;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        principal = UserPrincipal.create(userId, "mentee@fpt.edu.vn", List.of(RoleCode.MENTEE));
    }

    @Test
    void getRecommendations_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/mentors/recommendations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getRecommendations_authenticated_shouldReturn200() throws Exception {
        MentorDiscoveryCardResponse mentor = MentorDiscoveryCardResponse.builder()
                .mentorUserId(UUID.randomUUID())
                .displayName("Mentor A")
                .headline("Java Backend Mentor")
                .ratingAverage(new BigDecimal("4.80"))
                .reviewCount(12)
                .completedSessions(30)
                .expertiseTags(List.of(MentorTagResponse.builder()
                        .id(UUID.randomUUID())
                        .code("JAVA")
                        .nameVi("Java")
                        .build()))
                .build();

        when(mentorDiscoveryService.getRecommendations(userId, 8))
                .thenReturn(List.of(MentorRecommendationResponse.builder()
                        .mentor(mentor)
                        .matchScore(new BigDecimal("82.40"))
                        .matchReasons(List.of("Cùng chuyên ngành với mentee"))
                        .build()));

        mockMvc.perform(get("/api/mentors/recommendations")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].matchScore").value(82.4))
                .andExpect(jsonPath("$.data[0].mentor.displayName").value("Mentor A"));
    }

    @Test
    void searchMentors_authenticated_shouldReturn200() throws Exception {
        MentorDiscoveryCardResponse mentor = MentorDiscoveryCardResponse.builder()
                .mentorUserId(UUID.randomUUID())
                .displayName("Mentor B")
                .headline("Career Mentor")
                .ratingAverage(new BigDecimal("4.60"))
                .reviewCount(8)
                .completedSessions(16)
                .expertiseTags(List.of())
                .build();

        when(mentorDiscoveryService.searchMentors(eq(userId), any()))
                .thenReturn(PageResponse.<MentorDiscoveryCardResponse>builder()
                        .content(List.of(mentor))
                        .page(0)
                        .size(12)
                        .totalElements(1)
                        .totalPages(1)
                        .last(true)
                        .build());

        mockMvc.perform(get("/api/mentors")
                        .with(user(principal))
                        .param("keyword", "career")
                        .param("size", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].displayName").value("Mentor B"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void getMentorDetail_authenticated_shouldReturn200() throws Exception {
        UUID mentorUserId = UUID.randomUUID();
        when(mentorDiscoveryService.getMentorDetail(mentorUserId))
                .thenReturn(MentorDiscoveryDetailResponse.builder()
                        .mentorUserId(mentorUserId)
                        .displayName("Mentor Detail")
                        .headline("Java Mentor")
                        .services(List.of())
                        .expertiseTags(List.of())
                        .helpTopicTags(List.of())
                        .build());

        mockMvc.perform(get("/api/mentors/{mentorUserId}", mentorUserId)
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Mentor Detail"));
    }

    @Test
    void getMentorAvailability_authenticated_shouldReturn200() throws Exception {
        UUID mentorUserId = UUID.randomUUID();
        when(mentorDiscoveryService.getMentorAvailability(mentorUserId))
                .thenReturn(List.of(MentorAvailabilitySlotResponse.builder()
                        .slotId(UUID.randomUUID())
                        .startTime(LocalDateTime.of(2026, 6, 20, 9, 0))
                        .endTime(LocalDateTime.of(2026, 6, 20, 10, 0))
                        .timezone("Asia/Ho_Chi_Minh")
                        .durationMinutes(60)
                        .recurring(false)
                        .build()));

        mockMvc.perform(get("/api/mentors/{mentorUserId}/availability", mentorUserId)
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].durationMinutes").value(60));
    }

    @Test
    void getMentorReviews_authenticated_shouldReturn200() throws Exception {
        UUID mentorUserId = UUID.randomUUID();
        when(mentorDiscoveryService.getMentorReviews(eq(mentorUserId), any()))
                .thenReturn(PageResponse.<MentorReviewResponse>builder()
                        .content(List.of(MentorReviewResponse.builder()
                                .reviewId(UUID.randomUUID())
                                .reviewerUserId(UUID.randomUUID())
                                .reviewerDisplayName("Mentee A")
                                .rating(5)
                                .comment("Mentor hỗ trợ rất rõ ràng")
                                .createdAt(LocalDateTime.of(2026, 6, 20, 10, 0))
                                .build()))
                        .page(0)
                        .size(10)
                        .totalElements(1)
                        .totalPages(1)
                        .last(true)
                        .build());

        mockMvc.perform(get("/api/mentors/{mentorUserId}/reviews", mentorUserId)
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].reviewerDisplayName").value("Mentee A"))
                .andExpect(jsonPath("$.data.content[0].rating").value(5));
    }
}
