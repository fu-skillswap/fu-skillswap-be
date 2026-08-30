package com.fptu.exe.skillswap.modules.payment.port.dto;

import com.fptu.exe.skillswap.modules.payment.domain.CouponStatus;

public record AdminCouponStatusChangeCommand(
        CouponStatus status
) {}
