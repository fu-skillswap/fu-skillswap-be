package com.fptu.exe.skillswap.modules.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.dto.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.dto.CreateBookingRequest;
import com.fptu.exe.skillswap.modules.booking.service.BookingService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookingService bookingService;

    private UUID menteeUserId;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        menteeUserId = UUID.randomUUID();
        principal = UserPrincipal.create(menteeUserId, "mentee@fpt.edu.vn", List.of(RoleCode.MENTEE));
    }

    @Test
    void createBooking_unauthenticated_shouldReturn401() throws Exception {
        CreateBookingRequest request = new CreateBookingRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "Cần định hướng backend Java",
                "Muốn được review CV và lộ trình học"
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createBooking_authenticated_shouldReturn201() throws Exception {
        UUID mentorUserId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        CreateBookingRequest request = new CreateBookingRequest(
                mentorUserId,
                slotId,
                null,
                "Cần định hướng backend Java",
                "Muốn được review CV và lộ trình học"
        );

        when(bookingService.createBooking(eq(menteeUserId), eq(request)))
                .thenReturn(BookingResponse.builder()
                        .bookingId(UUID.randomUUID())
                        .mentorUserId(mentorUserId)
                        .mentorDisplayName("Mentor One")
                        .menteeUserId(menteeUserId)
                        .slotId(slotId)
                        .status(BookingStatus.PENDING)
                        .learningGoalTitle(request.learningGoalTitle())
                        .learningGoalDescription(request.learningGoalDescription())
                        .requestedStartTime(LocalDateTime.of(2026, 6, 20, 9, 0))
                        .requestedEndTime(LocalDateTime.of(2026, 6, 20, 10, 0))
                        .createdAt(LocalDateTime.of(2026, 6, 15, 10, 0))
                        .build());

        mockMvc.perform(post("/api/bookings")
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.mentorDisplayName").value("Mentor One"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }
}
