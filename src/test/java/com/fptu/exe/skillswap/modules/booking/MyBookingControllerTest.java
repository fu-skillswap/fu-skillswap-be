package com.fptu.exe.skillswap.modules.booking;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.dto.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.service.BookingService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

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
class MyBookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookingService bookingService;

    private UUID userId;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        principal = UserPrincipal.create(userId, "mentee@fpt.edu.vn", List.of(RoleCode.MENTEE));
    }

    @Test
    void getMyBookings_authenticated_shouldReturn200() throws Exception {
        when(bookingService.getMyBookings(eq(userId), any()))
                .thenReturn(PageResponse.<BookingResponse>builder()
                        .content(List.of(BookingResponse.builder()
                                .bookingId(UUID.randomUUID())
                                .mentorDisplayName("Mentor One")
                                .status(BookingStatus.PENDING)
                                .requestedStartTime(LocalDateTime.of(2026, 6, 20, 9, 0))
                                .requestedEndTime(LocalDateTime.of(2026, 6, 20, 10, 0))
                                .build()))
                        .page(0)
                        .size(10)
                        .totalElements(1)
                        .totalPages(1)
                        .last(true)
                        .build());

        mockMvc.perform(get("/api/me/bookings")
                        .with(user(principal))
                        .param("role", "MENTEE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].mentorDisplayName").value("Mentor One"));
    }

    @Test
    void getBookingDetail_authenticated_shouldReturn200() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(bookingService.getBookingDetail(userId, bookingId))
                .thenReturn(BookingResponse.builder()
                        .bookingId(bookingId)
                        .mentorDisplayName("Mentor One")
                        .status(BookingStatus.ACCEPTED)
                        .build());

        mockMvc.perform(get("/api/me/bookings/{bookingId}", bookingId)
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
    }
}
