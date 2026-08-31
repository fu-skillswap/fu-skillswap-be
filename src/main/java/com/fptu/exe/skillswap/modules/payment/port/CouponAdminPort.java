package com.fptu.exe.skillswap.modules.payment.port;

import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public interface CouponAdminPort {
    PageResponse<CouponView> list(CouponListQuery query);
    CouponView getDetail(UUID couponId);
    CouponView create(UUID adminUserId, CreateCouponCommand command);
    CouponView update(UUID adminUserId, UUID couponId, UpdateCouponCommand command);
    CouponView changeStatus(UUID adminUserId, UUID couponId, ChangeCouponStatusCommand command);
    PageResponse<CouponRedemptionView> getRedemptions(UUID couponId, CouponPageQuery query);

    record CouponListQuery(String status, String discountType, String keyword, int page, int size, String sortBy, String direction) { }
    record CouponPageQuery(int page, int size, String sortBy, String direction) { }
    record CreateCouponCommand(String code, String title, String description, String discountType, Integer discountValue, Integer maxDiscountScoin, LocalDateTime startAt, LocalDateTime endAt, Integer quotaTotal, Integer quotaPerUser, Integer minOrderValueScoin, Set<UUID> applicableServiceIds, Set<UUID> applicableMentorIds) { }
    record UpdateCouponCommand(String code, String title, String description, String discountType, Integer discountValue, Integer maxDiscountScoin, LocalDateTime startAt, LocalDateTime endAt, Integer quotaTotal, Integer quotaPerUser, Integer minOrderValueScoin, Set<UUID> applicableServiceIds, Set<UUID> applicableMentorIds) { }
    record ChangeCouponStatusCommand(String status) { }
    record CouponView(UUID id, String code, String title, String description, String discountType, Integer discountValue, Integer maxDiscountScoin, String status, LocalDateTime startAt, LocalDateTime endAt, Integer quotaTotal, Integer quotaPerUser, Integer minOrderValueScoin, Set<UUID> applicableServiceIds, Set<UUID> applicableMentorIds, long totalRedemptions, long activeRedemptions, LocalDateTime createdAt, LocalDateTime updatedAt) { }
    record CouponRedemptionView(UUID id, UUID couponId, UUID paymentOrderId, UUID redeemerUserId, String redeemerFullName, String status, Integer discountScoin, LocalDateTime createdAt) { }
}
