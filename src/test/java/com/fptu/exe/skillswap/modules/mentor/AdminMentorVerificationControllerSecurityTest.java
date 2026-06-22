package com.fptu.exe.skillswap.modules.mentor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminMentorVerificationControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "MENTEE")
    void nonAdmin_shouldNotAccessQueue() throws Exception {
        mockMvc.perform(get("/api/admin/mentor-verification/requests"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MENTEE")
    void nonAdmin_shouldNotAccessDetail() throws Exception {
        mockMvc.perform(get("/api/admin/mentor-verification/requests/{requestId}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MENTEE")
    void nonAdmin_shouldNotRefreshLock() throws Exception {
        mockMvc.perform(post("/api/admin/mentor-verification/requests/{requestId}/lock/refresh", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MENTEE")
    void nonAdmin_shouldNotApprove() throws Exception {
        mockMvc.perform(post("/api/admin/mentor-verification/requests/{requestId}/approve", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"OK\"}"))
                .andExpect(status().isForbidden());
    }
}
