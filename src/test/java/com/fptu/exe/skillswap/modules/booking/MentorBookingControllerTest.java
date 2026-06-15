package com.fptu.exe.skillswap.modules.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.dto.AcceptBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.dto.RejectBookingRequest;
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
class MentorBookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookingService bookingService;

    private UUID mentorUserId;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        mentorUserId = UUID.randomUUID();
        principal = UserPrincipal.create(mentorUserId, "mentor@fpt.edu.vn", List.of(RoleCode.MENTOR));
    }

    @Test
    void acceptBooking_authenticatedMentor_shouldReturn200() throws Exception {
        UUID bookingId = UUID.randomUUID();
        AcceptBookingRequest request = new AcceptBookingRequest("Hẹn gặp em vào buổi này nhé");
        when(bookingService.acceptBooking(eq(mentorUserId), eq(bookingId), eq(request)))
                .thenReturn(BookingResponse.builder()
                        .bookingId(bookingId)
                        .status(BookingStatus.ACCEPTED)
                        .mentorResponseNote("Hẹn gặp em vào buổi này nhé")
                        .build());

        mockMvc.perform(post("/api/mentor/bookings/{bookingId}/accept", bookingId)
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
    }

    @Test
    void rejectBooking_authenticatedMentor_shouldReturn200() throws Exception {
        UUID bookingId = UUID.randomUUID();
        RejectBookingRequest request = new RejectBookingRequest("Mentor bận lịch này", "Em chọn giúp anh slot khác nhé");
        when(bookingService.rejectBooking(eq(mentorUserId), eq(bookingId), eq(request)))
                .thenReturn(BookingResponse.builder()
                        .bookingId(bookingId)
                        .status(BookingStatus.REJECTED)
                        .rejectReason("Mentor bận lịch này")
                        .build());

        mockMvc.perform(post("/api/mentor/bookings/{bookingId}/reject", bookingId)
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("Mentor bận lịch này"));
    }
}
