package com.fptu.exe.skillswap.modules.payment.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.payment.dto.response.CreditWalletResponse;
import com.fptu.exe.skillswap.modules.payment.dto.response.MentorWalletResponse;
import com.fptu.exe.skillswap.modules.payment.service.WalletQueryService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WalletControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WalletQueryService walletQueryService;

    @Test
    void getCreditWallet_adminShouldBeForbidden() throws Exception {
        mockMvc.perform(get("/api/me/credit-wallet")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(walletQueryService);
    }

    @Test
    void getMentorWallet_adminShouldBeForbidden() throws Exception {
        mockMvc.perform(get("/api/me/mentor-wallet")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(walletQueryService);
    }

    @Test
    void getCreditWallet_menteeShouldBeAllowed() throws Exception {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(userId, "mentee@test.com", List.of(RoleCode.MENTEE));
        when(walletQueryService.getMyCreditWallet(eq(userId))).thenReturn(CreditWalletResponse.builder().availableScoin(100).build());

        mockMvc.perform(get("/api/me/credit-wallet")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                principal.getAuthorities()
                        ))))
                .andExpect(status().isOk());

        verify(walletQueryService).getMyCreditWallet(userId);
    }

    @Test
    void getMentorWallet_mentorShouldBeAllowed() throws Exception {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(userId, "mentor@test.com", List.of(RoleCode.MENTOR));
        when(walletQueryService.getMyMentorWallet(eq(userId))).thenReturn(MentorWalletResponse.builder().availableScoin(200).build());

        mockMvc.perform(get("/api/me/mentor-wallet")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                principal.getAuthorities()
                        ))))
                .andExpect(status().isOk());

        verify(walletQueryService).getMyMentorWallet(userId);
    }
}
