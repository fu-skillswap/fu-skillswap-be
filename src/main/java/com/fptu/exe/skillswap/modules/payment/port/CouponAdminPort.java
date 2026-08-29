package com.fptu.exe.skillswap.modules.payment.port;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCouponCreateRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCouponListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCouponStatusRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCouponUpdateRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminCouponRedemptionResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminCouponResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CouponAdminPort {
    PageResponse<AdminCouponResponse> list(AdminCouponListRequest request);
    AdminCouponResponse getDetail(UUID couponId);
    AdminCouponResponse create(UUID adminUserId, AdminCouponCreateRequest request);
    AdminCouponResponse update(UUID adminUserId, UUID couponId, AdminCouponUpdateRequest request);
    AdminCouponResponse changeStatus(UUID adminUserId, UUID couponId, AdminCouponStatusRequest request);
    PageResponse<AdminCouponRedemptionResponse> getRedemptions(UUID couponId, Pageable pageable);
}
