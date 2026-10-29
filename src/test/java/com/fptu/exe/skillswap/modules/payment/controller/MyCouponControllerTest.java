package com.fptu.exe.skillswap.modules.payment.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.payment.domain.CouponDiscountType;
import com.fptu.exe.skillswap.modules.payment.dto.response.UserCouponResponse;
import com.fptu.exe.skillswap.modules.payment.service.CouponService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MyCouponControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CouponService couponService;

    @Test
    void getMyCoupons_unauthenticated_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/me/coupons"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(couponService);
    }

    @Test
    void getMyCoupons_asAdmin_shouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/me/coupons")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(couponService);
    }

    @Test
    void getMyCoupons_asMentee_shouldReturnOkWithCoupons() throws Exception {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(userId, "mentee@test.com", List.of(RoleCode.MENTEE));

        UserCouponResponse couponResponse = UserCouponResponse.builder()
                .id(UUID.randomUUID())
                .code("WELCOME70K")
                .title("Mã chào mừng thành viên mới")
                .discountType(CouponDiscountType.FIXED)
                .discountValue(70000)
                .quotaTotal(60)
                .remainingQuota(59)
                .isUsable(true)
                .build();

        when(couponService.getAvailableCouponsForUser(eq(userId))).thenReturn(List.of(couponResponse));

        mockMvc.perform(get("/api/me/coupons")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                principal.getAuthorities()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].code").value("WELCOME70K"))
                .andExpect(jsonPath("$.data[0].discountValue").value(70000))
                .andExpect(jsonPath("$.data[0].remainingQuota").value(59))
                .andExpect(jsonPath("$.data[0].isUsable").value(true));

        verify(couponService).getAvailableCouponsForUser(userId);
    }
}
