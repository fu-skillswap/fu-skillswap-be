package com.fptu.exe.skillswap.modules.payment.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentCheckoutRequest;
import com.fptu.exe.skillswap.modules.payment.dto.response.PaymentCheckoutResponse;
import com.fptu.exe.skillswap.modules.payment.service.PaymentOrderService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.ratelimit.InMemoryRateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentControllerMobileBlockTest {

    @Mock
    private PaymentOrderService paymentOrderService;

    @Mock
    private InMemoryRateLimitService rateLimitService;

    @InjectMocks
    private PaymentController paymentController;

    @Test
    void checkout_withAndroidPlatformHeader_shouldThrowForbiddenException() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(userId, "test@fpt.edu.vn", List.of(RoleCode.MENTEE));
        PaymentCheckoutRequest request = new PaymentCheckoutRequest(UUID.randomUUID(), null);
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getHeader("X-Client-Platform")).thenReturn("android");

        BaseException ex = assertThrows(BaseException.class, () ->
                paymentController.checkout(principal, request, httpRequest));

        assertEquals(ErrorCode.PAYMENT_METHOD_NOT_SUPPORTED_ON_MOBILE, ex.getErrorCode());
        assertEquals("Tính năng nạp tiền và thanh toán trực tiếp không khả dụng trên phiên bản ứng dụng di động.", ex.getMessage());
        verify(paymentOrderService, never()).checkout(any(), any());
    }

    @Test
    void checkout_withWebPlatform_shouldProceedToService() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(userId, "test@fpt.edu.vn", List.of(RoleCode.MENTEE));
        PaymentCheckoutRequest request = new PaymentCheckoutRequest(UUID.randomUUID(), null);
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getHeader("X-Client-Platform")).thenReturn("web");

        PaymentCheckoutResponse mockResponse = mock(PaymentCheckoutResponse.class);
        when(paymentOrderService.checkout(eq(userId), eq(request))).thenReturn(mockResponse);

        var responseEntity = paymentController.checkout(principal, request, httpRequest);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.CREATED, responseEntity.getStatusCode());
        verify(paymentOrderService).checkout(userId, request);
    }
}
