package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCouponCreateRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCouponListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCouponStatusRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCouponUpdateRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminCouponRedemptionResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminCouponResponse;
import com.fptu.exe.skillswap.modules.payment.port.CouponAdminPort;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCouponCreateCommand;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCouponDto;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCouponFilterQuery;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCouponRedemptionDto;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCouponStatusChangeCommand;
import com.fptu.exe.skillswap.modules.payment.port.dto.AdminCouponUpdateCommand;
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
        AdminCouponFilterQuery query = new AdminCouponFilterQuery();
        if (request != null) {
            query.setStatus(request.getStatus());
            query.setDiscountType(request.getDiscountType());
            query.setKeyword(request.getKeyword());
            query.setPage(request.getPage());
            query.setSize(request.getSize());
            query.setSortBy(request.getSortBy());
            query.setDirection(request.getDirection());
        }
        PageResponse<AdminCouponDto> result = couponAdminPort.list(query);
        return PageResponse.<AdminCouponResponse>builder()
                .content(result.getContent().stream().map(this::toResponse).toList())
                .page(result.getPage())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public AdminCouponResponse getDetail(UUID couponId) {
        return toResponse(couponAdminPort.getDetail(couponId));
    }

    @Transactional
    public AdminCouponResponse create(UUID adminUserId, AdminCouponCreateRequest request) {
        AdminCouponCreateCommand command = new AdminCouponCreateCommand(
                request.code(),
                request.title(),
                request.description(),
                request.discountType(),
                request.discountValue(),
                request.maxDiscountScoin(),
                request.startAt(),
                request.endAt(),
                request.quotaTotal(),
                request.quotaPerUser(),
                request.minOrderValueScoin(),
                request.applicableServiceIds(),
                request.applicableMentorIds()
        );
        return toResponse(couponAdminPort.create(adminUserId, command));
    }

    @Transactional
    public AdminCouponResponse update(UUID adminUserId, UUID couponId, AdminCouponUpdateRequest request) {
        AdminCouponUpdateCommand command = new AdminCouponUpdateCommand(
                request.code(),
                request.title(),
                request.description(),
                request.discountType(),
                request.discountValue(),
                request.maxDiscountScoin(),
                request.startAt(),
                request.endAt(),
                request.quotaTotal(),
                request.quotaPerUser(),
                request.minOrderValueScoin(),
                request.applicableServiceIds(),
                request.applicableMentorIds()
        );
        return toResponse(couponAdminPort.update(adminUserId, couponId, command));
    }

    @Transactional
    public AdminCouponResponse changeStatus(UUID adminUserId, UUID couponId, AdminCouponStatusRequest request) {
        AdminCouponStatusChangeCommand command = new AdminCouponStatusChangeCommand(request.status());
        return toResponse(couponAdminPort.changeStatus(adminUserId, couponId, command));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminCouponRedemptionResponse> getRedemptions(UUID couponId, Pageable pageable) {
        PageResponse<AdminCouponRedemptionDto> result = couponAdminPort.getRedemptions(couponId, pageable);
        return PageResponse.<AdminCouponRedemptionResponse>builder()
                .content(result.getContent().stream().map(this::toRedemptionResponse).toList())
                .page(result.getPage())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build();
    }

    private AdminCouponResponse toResponse(AdminCouponDto dto) {
        if (dto == null) {
            return null;
        }
        return new AdminCouponResponse(
                dto.id(),
                dto.code(),
                dto.title(),
                dto.description(),
                dto.discountType(),
                dto.discountValue(),
                dto.maxDiscountScoin(),
                dto.status(),
                dto.startAt(),
                dto.endAt(),
                dto.quotaTotal(),
                dto.quotaPerUser(),
                dto.minOrderValueScoin(),
                dto.applicableServiceIds(),
                dto.applicableMentorIds(),
                dto.totalRedemptions(),
                dto.activeRedemptions(),
                dto.createdAt(),
                dto.updatedAt()
        );
    }

    private AdminCouponRedemptionResponse toRedemptionResponse(AdminCouponRedemptionDto dto) {
        if (dto == null) {
            return null;
        }
        return new AdminCouponRedemptionResponse(
                dto.id(),
                dto.couponId(),
                dto.paymentOrderId(),
                dto.redeemerUserId(),
                dto.redeemerFullName(),
                dto.status(),
                dto.discountScoin(),
                dto.createdAt()
        );
    }
}
