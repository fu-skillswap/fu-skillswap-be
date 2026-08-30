package com.fptu.exe.skillswap.modules.payment.port;

import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCouponCreateCommand;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCouponDto;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCouponFilterQuery;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCouponRedemptionDto;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCouponStatusChangeCommand;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCouponUpdateCommand;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CouponAdminPort {
    PageResponse<AdminCouponDto> list(AdminCouponFilterQuery query);
    AdminCouponDto getDetail(UUID couponId);
    AdminCouponDto create(UUID adminUserId, AdminCouponCreateCommand command);
    AdminCouponDto update(UUID adminUserId, UUID couponId, AdminCouponUpdateCommand command);
    AdminCouponDto changeStatus(UUID adminUserId, UUID couponId, AdminCouponStatusChangeCommand command);
    PageResponse<AdminCouponRedemptionDto> getRedemptions(UUID couponId, Pageable pageable);
}
