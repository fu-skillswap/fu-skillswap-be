package com.fptu.exe.skillswap.modules.payment.port;

import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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

    record CouponListQuery(String status, String discountType, String keyword, Integer page, Integer size, String sortBy, String direction) { }
    record CouponPageQuery(Integer page, Integer size, String sortBy, String direction) { }
    record CreateCouponCommand(
            @NotBlank(message = "Mã coupon không được để trống") @Size(max = 80, message = "Mã coupon không quá 80 ký tự") String code,
            @NotBlank(message = "Tiêu đề coupon không được để trống") @Size(max = 150, message = "Tiêu đề coupon không quá 150 ký tự") String title,
            String description,
            @NotBlank(message = "Loại giảm giá không được để trống") String discountType,
            @NotNull(message = "Giá trị giảm giá không được để trống") @Min(value = 1, message = "Giá trị giảm giá phải >= 1") Integer discountValue,
            @Min(value = 0, message = "Giảm tối đa phải >= 0") Integer maxDiscountScoin,
            LocalDateTime startAt,
            LocalDateTime endAt,
            @Min(value = 1, message = "Quota tổng phải >= 1") Integer quotaTotal,
            @Min(value = 1, message = "Quota mỗi user phải >= 1") Integer quotaPerUser,
            @Min(value = 0, message = "Giá trị đơn tối thiểu phải >= 0") Integer minOrderValueScoin,
            Set<UUID> applicableServiceIds,
            Set<UUID> applicableMentorIds
    ) { }
    record UpdateCouponCommand(
            @Size(max = 80, message = "Mã coupon không quá 80 ký tự") String code,
            @Size(max = 150, message = "Tiêu đề coupon không quá 150 ký tự") String title,
            String description,
            String discountType,
            @Min(value = 1, message = "Giá trị giảm giá phải >= 1") Integer discountValue,
            @Min(value = 0, message = "Giảm tối đa phải >= 0") Integer maxDiscountScoin,
            LocalDateTime startAt,
            LocalDateTime endAt,
            @Min(value = 1, message = "Quota tổng phải >= 1") Integer quotaTotal,
            @Min(value = 1, message = "Quota mỗi user phải >= 1") Integer quotaPerUser,
            @Min(value = 0, message = "Giá trị đơn tối thiểu phải >= 0") Integer minOrderValueScoin,
            Set<UUID> applicableServiceIds,
            Set<UUID> applicableMentorIds
    ) { }
    record ChangeCouponStatusCommand(@NotBlank(message = "Trạng thái coupon không được để trống") String status) { }
    record CouponView(UUID id, String code, String title, String description, String discountType, Integer discountValue, Integer maxDiscountScoin, String status, LocalDateTime startAt, LocalDateTime endAt, Integer quotaTotal, Integer quotaPerUser, Integer minOrderValueScoin, Set<UUID> applicableServiceIds, Set<UUID> applicableMentorIds, long totalRedemptions, long activeRedemptions, LocalDateTime createdAt, LocalDateTime updatedAt) { }
    record CouponRedemptionView(UUID id, UUID couponId, UUID paymentOrderId, UUID redeemerUserId, String redeemerFullName, String status, Integer discountScoin, LocalDateTime createdAt) { }
}
