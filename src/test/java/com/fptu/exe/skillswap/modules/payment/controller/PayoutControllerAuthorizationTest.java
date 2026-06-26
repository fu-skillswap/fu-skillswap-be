package com.fptu.exe.skillswap.modules.payment.controller;

import com.fptu.exe.skillswap.modules.payment.service.PayoutService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PayoutControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PayoutService payoutService;

    @Test
    @WithMockUser(roles = "MENTOR")
    void adminList_mentorShouldReceiveForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/payout-requests"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(payoutService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminDetail_adminShouldBeAllowed() throws Exception {
        mockMvc.perform(get("/api/admin/payout-requests/" + UUID.randomUUID()))
                .andExpect(status().isOk());
    }
}
