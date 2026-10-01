package com.fptu.exe.skillswap.modules.payment.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentCheckoutRequest;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConsumptionOnlyAppIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        testUser = userRepository.saveAndFlush(User.builder()
                .email("mentee.consumption." + UUID.randomUUID() + "@fpt.edu.vn")
                .fullName("Mentee Tester")
                .status(UserStatus.ACTIVE)
                .roles(new HashSet<>(Set.of(RoleCode.MENTEE)))
                .build());

        userPrincipal = UserPrincipal.create(
                testUser.getId(),
                testUser.getEmail(),
                List.of(RoleCode.MENTEE)
        );
    }

    @Test
    void getAppConfig_forAndroid_shouldReturnConsumptionOnlyTrue() throws Exception {
        mockMvc.perform(get("/api/public/app-config")
                        .param("platform", "android"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.data.platform", is("android")))
                .andExpect(jsonPath("$.data.consumptionOnly", is(true)))
                .andExpect(jsonPath("$.data.topupEnabled", is(false)))
                .andExpect(jsonPath("$.data.directPaymentEnabled", is(false)));
    }

    @Test
    void getAppConfig_forWeb_shouldReturnConsumptionOnlyFalse() throws Exception {
        mockMvc.perform(get("/api/public/app-config")
                        .param("platform", "web"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.data.platform", is("web")))
                .andExpect(jsonPath("$.data.consumptionOnly", is(false)))
                .andExpect(jsonPath("$.data.topupEnabled", is(true)))
                .andExpect(jsonPath("$.data.directPaymentEnabled", is(true)));
    }
}
