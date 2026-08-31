package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.payment.port.CouponAdminPort;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminCouponService {

    private final CouponAdminPort couponAdminPort;

    @Transactional(readOnly = true)
    public PageResponse<CouponAdminPort.CouponView> list(CouponAdminPort.CouponListQuery request) {
        return couponAdminPort.list(request);
    }

    @Transactional(readOnly = true)
    public CouponAdminPort.CouponView getDetail(UUID couponId) {
        return couponAdminPort.getDetail(couponId);
    }

    @Transactional
    public CouponAdminPort.CouponView create(UUID adminUserId, CouponAdminPort.CreateCouponCommand request) {
        return couponAdminPort.create(adminUserId, request);
    }

    @Transactional
    public CouponAdminPort.CouponView update(UUID adminUserId, UUID couponId, CouponAdminPort.UpdateCouponCommand request) {
        return couponAdminPort.update(adminUserId, couponId, request);
    }

    @Transactional
    public CouponAdminPort.CouponView changeStatus(UUID adminUserId, UUID couponId, CouponAdminPort.ChangeCouponStatusCommand request) {
        return couponAdminPort.changeStatus(adminUserId, couponId, request);
    }

    @Transactional(readOnly = true)
    public PageResponse<CouponAdminPort.CouponRedemptionView> getRedemptions(UUID couponId, CouponAdminPort.CouponPageQuery query) {
        return couponAdminPort.getRedemptions(couponId, query);
    }
}
