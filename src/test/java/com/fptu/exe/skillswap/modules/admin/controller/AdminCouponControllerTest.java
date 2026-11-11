package com.fptu.exe.skillswap.modules.admin.controller;

import com.fptu.exe.skillswap.modules.admin.service.AdminCouponService;
import com.fptu.exe.skillswap.modules.payment.port.CouponAdminPort;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminCouponControllerTest {

    @Mock
    private CouponAdminPort couponAdminPort;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminCouponService service = new AdminCouponService(couponAdminPort);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminCouponController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    void listCoupons_withDefaultAndEmptyQueryParams_bindsCleanlyWithout500() throws Exception {
        CouponAdminPort.CouponView sample = new CouponAdminPort.CouponView(
                UUID.randomUUID(), "WELCOME70K", "Welcome", "Desc", "FIXED", 70000,
                null, "ACTIVE", null, null, 60, 1, 0, Set.of(), Set.of(), 0, 0,
                LocalDateTime.now(), LocalDateTime.now()
        );
        when(couponAdminPort.list(any())).thenReturn(PageResponse.<CouponAdminPort.CouponView>builder()
                .content(List.of(sample))
                .page(0).size(20).totalElements(1).totalPages(1).last(true)
                .build());

        // 1. Exact request from production screenshot
        mockMvc.perform(get("/api/admin/coupons")
                        .param("sortBy", "createdAt")
                        .param("direction", "DESC")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content[0].code").value("WELCOME70K"));

        // 2. No query params at all (page and size are null in record, must NOT throw BeanInstantiationException)
        mockMvc.perform(get("/api/admin/coupons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].code").value("WELCOME70K"));

        ArgumentCaptor<CouponAdminPort.CouponListQuery> captor = ArgumentCaptor.forClass(CouponAdminPort.CouponListQuery.class);
        verify(couponAdminPort, org.mockito.Mockito.times(2)).list(captor.capture());
        assertThat(captor.getAllValues().get(1).page()).isNull();
        assertThat(captor.getAllValues().get(1).size()).isNull();
    }

    @Test
    void redemptions_withoutQueryParams_bindsCleanly() throws Exception {
        UUID couponId = UUID.randomUUID();
        when(couponAdminPort.getRedemptions(eq(couponId), any())).thenReturn(PageResponse.<CouponAdminPort.CouponRedemptionView>builder()
                .content(List.of()).page(0).size(20).totalElements(0).totalPages(0).last(true).build());

        mockMvc.perform(get("/api/admin/coupons/{couponId}/redemptions", couponId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    void createCoupon_withMissingRequiredFields_returns400ValidationError() throws Exception {
        mockMvc.perform(post("/api/admin/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
