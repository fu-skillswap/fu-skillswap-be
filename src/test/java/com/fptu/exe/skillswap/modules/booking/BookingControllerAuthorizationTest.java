package com.fptu.exe.skillswap.modules.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateBookingRequest;
import com.fptu.exe.skillswap.modules.booking.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookingService bookingService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void createBooking_adminShouldReceiveForbidden() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(bookingService);
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void createBooking_systemAdminShouldReceiveForbidden() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(bookingService);
    }

    @Test
    @WithMockUser(roles = "MENTEE")
    void createBooking_malformedJsonShouldReturnClearErrorEnvelope() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VAL_3001"))
                .andExpect(jsonPath("$.message").value("Body request không hợp lệ hoặc không đúng định dạng mà API hỗ trợ"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verifyNoInteractions(bookingService);
    }

    @Test
    @WithMockUser(roles = "MENTEE")
    void createBooking_unsupportedContentTypeShouldReturnClearErrorEnvelope() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not-json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.code").value("SYS_0009"))
                .andExpect(jsonPath("$.message").value("Content-Type không được hỗ trợ"))
                .andExpect(jsonPath("$.data[0].field").value("Content-Type"));

        verifyNoInteractions(bookingService);
    }

    private CreateBookingRequest validRequest() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 26, 10, 0);
        return new CreateBookingRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                start,
                start.plusHours(1),
                "Cần hỗ trợ Spring Boot",
                "Muốn hiểu rõ transaction và JPA locking"
        );
    }
}
