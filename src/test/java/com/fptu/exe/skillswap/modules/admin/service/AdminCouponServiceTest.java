package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCouponCreateRequest;
import com.fptu.exe.skillswap.modules.payment.port.CouponAdminPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCouponServiceTest {
    @Mock private CouponAdminPort couponAdminPort;
    @InjectMocks private AdminCouponService service;

    @Test
    void create_delegatesToCouponAdminPort() {
        UUID adminId = UUID.randomUUID();
        when(couponAdminPort.create(eq(adminId), isNull(AdminCouponCreateRequest.class))).thenReturn(null);

        assertThat(service.create(adminId, null)).isNull();

        verify(couponAdminPort).create(eq(adminId), isNull(AdminCouponCreateRequest.class));
    }
}
