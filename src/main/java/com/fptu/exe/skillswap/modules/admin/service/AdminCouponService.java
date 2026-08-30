package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCouponCreateRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCouponListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCouponStatusRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCouponUpdateRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminCouponRedemptionResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminCouponResponse;
import com.fptu.exe.skillswap.modules.payment.port.CouponAdminPort;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminCouponService {

    private final CouponAdminPort couponAdminPort;

    @Transactional(readOnly = true)
    public PageResponse<AdminCouponResponse> list(AdminCouponListRequest request) {
        return couponAdminPort.list(request);
    }

    @Transactional(readOnly = true)
    public AdminCouponResponse getDetail(UUID couponId) {
        return couponAdminPort.getDetail(couponId);
    }

    @Transactional
    public AdminCouponResponse create(UUID adminUserId, AdminCouponCreateRequest request) {
        return couponAdminPort.create(adminUserId, request);
    }

    @Transactional
    public AdminCouponResponse update(UUID adminUserId, UUID couponId, AdminCouponUpdateRequest request) {
        return couponAdminPort.update(adminUserId, couponId, request);
    }

    @Transactional
    public AdminCouponResponse changeStatus(UUID adminUserId, UUID couponId, AdminCouponStatusRequest request) {
        return couponAdminPort.changeStatus(adminUserId, couponId, request);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminCouponRedemptionResponse> getRedemptions(UUID couponId, Pageable pageable) {
        return couponAdminPort.getRedemptions(couponId, pageable);
    }
}
