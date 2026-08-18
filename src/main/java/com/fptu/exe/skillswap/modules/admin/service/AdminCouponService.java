package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCouponCreateRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCouponListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCouponStatusRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCouponUpdateRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminCouponRedemptionResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminCouponResponse;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.payment.domain.Coupon;
import com.fptu.exe.skillswap.modules.payment.domain.CouponDiscountType;
import com.fptu.exe.skillswap.modules.payment.domain.CouponRedemption;
import com.fptu.exe.skillswap.modules.payment.domain.CouponRedemptionStatus;
import com.fptu.exe.skillswap.modules.payment.domain.CouponStatus;
import com.fptu.exe.skillswap.modules.payment.repository.CouponRedemptionRepository;
import com.fptu.exe.skillswap.modules.payment.repository.CouponRepository;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminCouponService {

    private static final List<CouponRedemptionStatus> ACTIVE_REDEMPTION_STATUSES =
            List.of(CouponRedemptionStatus.RESERVED, CouponRedemptionStatus.REDEEMED);

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<AdminCouponResponse> list(AdminCouponListRequest request) {
        Specification<Coupon> spec = buildSpecification(request);
        Pageable pageable = request.getPageable();
        Page<Coupon> page = couponRepository.findAll(spec, pageable);

        List<AdminCouponResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .toList();

        return PageResponse.<AdminCouponResponse>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public AdminCouponResponse getDetail(UUID couponId) {
        Coupon coupon = findCouponOrThrow(couponId);
        return toResponse(coupon);
    }

    @Transactional
    public AdminCouponResponse create(UUID adminUserId, AdminCouponCreateRequest request) {
        String cleanCode = request.code().trim().toUpperCase(Locale.ROOT);
        if (couponRepository.existsByCode(cleanCode)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mã coupon '" + cleanCode + "' đã tồn tại trên hệ thống");
        }

        validateDiscountValue(request.discountType(), request.discountValue());
        validateTimeWindow(request.startAt(), request.endAt());

        Coupon coupon = Coupon.builder()
                .code(cleanCode)
                .title(request.title().trim())
                .description(request.description())
                .discountType(request.discountType())
                .discountValue(request.discountValue())
                .maxDiscountScoin(request.maxDiscountScoin())
                .status(CouponStatus.ACTIVE)
                .startAt(request.startAt())
                .endAt(request.endAt())
                .quotaTotal(request.quotaTotal())
                .quotaPerUser(request.quotaPerUser())
                .minOrderValueScoin(request.minOrderValueScoin())
                .applicableServiceIds(request.applicableServiceIds() == null ? new HashSet<>() : new HashSet<>(request.applicableServiceIds()))
                .applicableMentorIds(request.applicableMentorIds() == null ? new HashSet<>() : new HashSet<>(request.applicableMentorIds()))
                .build();

        Coupon saved = couponRepository.save(coupon);
        log.info("Admin {} created coupon {} with code {}", adminUserId, saved.getId(), cleanCode);
        return toResponse(saved);
    }

    @Transactional
    public AdminCouponResponse update(UUID adminUserId, UUID couponId, AdminCouponUpdateRequest request) {
        Coupon coupon = findCouponOrThrow(couponId);

        if (coupon.getStatus() == CouponStatus.EXPIRED) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Không thể cập nhật coupon đã hết hạn (EXPIRED)");
        }

        if (request.code() != null && !request.code().isBlank()) {
            String cleanCode = request.code().trim().toUpperCase(Locale.ROOT);
            if (couponRepository.existsByCodeAndIdNot(cleanCode, couponId)) {
                throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mã coupon '" + cleanCode + "' đã tồn tại trên hệ thống");
            }
            coupon.setCode(cleanCode);
        }

        CouponDiscountType targetType = request.discountType() != null ? request.discountType() : coupon.getDiscountType();
        Integer targetValue = request.discountValue() != null ? request.discountValue() : coupon.getDiscountValue();
        validateDiscountValue(targetType, targetValue);

        LocalDateTime newStart = request.startAt() != null ? request.startAt() : coupon.getStartAt();
        LocalDateTime newEnd = request.endAt() != null ? request.endAt() : coupon.getEndAt();
        validateTimeWindow(newStart, newEnd);

        if (request.title() != null && !request.title().isBlank()) coupon.setTitle(request.title().trim());
        if (request.description() != null) coupon.setDescription(request.description());
        if (request.discountType() != null) coupon.setDiscountType(request.discountType());
        if (request.discountValue() != null) coupon.setDiscountValue(request.discountValue());
        if (request.maxDiscountScoin() != null) coupon.setMaxDiscountScoin(request.maxDiscountScoin());
        coupon.setStartAt(request.startAt());
        coupon.setEndAt(request.endAt());
        if (request.quotaTotal() != null) coupon.setQuotaTotal(request.quotaTotal());
        if (request.quotaPerUser() != null) coupon.setQuotaPerUser(request.quotaPerUser());
        if (request.minOrderValueScoin() != null) coupon.setMinOrderValueScoin(request.minOrderValueScoin());

        if (request.applicableServiceIds() != null) coupon.setApplicableServiceIds(new HashSet<>(request.applicableServiceIds()));
        if (request.applicableMentorIds() != null) coupon.setApplicableMentorIds(new HashSet<>(request.applicableMentorIds()));

        Coupon saved = couponRepository.save(coupon);
        log.info("Admin {} updated coupon {}", adminUserId, saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public AdminCouponResponse changeStatus(UUID adminUserId, UUID couponId, AdminCouponStatusRequest request) {
        Coupon coupon = findCouponOrThrow(couponId);
        CouponStatus targetStatus = request.status();

        if (coupon.getStatus() == CouponStatus.EXPIRED) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Không thể chuyển trạng thái coupon đã hết hạn (EXPIRED)");
        }

        coupon.setStatus(targetStatus);
        Coupon saved = couponRepository.save(coupon);
        log.info("Admin {} changed coupon {} status to {}", adminUserId, saved.getId(), targetStatus);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminCouponRedemptionResponse> getRedemptions(UUID couponId, Pageable pageable) {
        findCouponOrThrow(couponId);
        Page<CouponRedemption> page = couponRedemptionRepository.findByCouponId(couponId, pageable);

        Set<UUID> userIds = page.getContent().stream()
                .map(CouponRedemption::getRedeemerUserId)
                .collect(Collectors.toSet());

        Map<UUID, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<AdminCouponRedemptionResponse> content = page.getContent().stream()
                .map(r -> {
                    User user = userMap.get(r.getRedeemerUserId());
                    String name = user == null ? "Unknown User" : user.getFullName();
                    return new AdminCouponRedemptionResponse(
                            r.getId(),
                            r.getCouponId(),
                            r.getPaymentOrderId(),
                            r.getRedeemerUserId(),
                            name,
                            r.getStatus(),
                            r.getDiscountScoin(),
                            r.getCreatedAt()
                    );
                })
                .toList();

        return PageResponse.<AdminCouponRedemptionResponse>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    // --- Private Helpers ---

    private Coupon findCouponOrThrow(UUID couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy coupon"));
    }

    private void validateDiscountValue(CouponDiscountType type, Integer value) {
        if (value == null || value <= 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Giá trị giảm giá phải lớn hơn 0");
        }
        if (type == CouponDiscountType.PERCENT && value > 100) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Tỷ lệ phần trăm giảm giá không được vượt quá 100%");
        }
    }

    private void validateTimeWindow(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt != null && endAt != null && !endAt.isAfter(startAt)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thời điểm hết hạn phải sau thời điểm bắt đầu");
        }
    }

    private AdminCouponResponse toResponse(Coupon c) {
        long totalRedemptions = couponRedemptionRepository.countByCouponId(c.getId());
        long activeRedemptions = couponRedemptionRepository.countByCouponIdAndStatusIn(c.getId(), ACTIVE_REDEMPTION_STATUSES);

        return new AdminCouponResponse(
                c.getId(),
                c.getCode(),
                c.getTitle(),
                c.getDescription(),
                c.getDiscountType(),
                c.getDiscountValue(),
                c.getMaxDiscountScoin(),
                c.getStatus(),
                c.getStartAt(),
                c.getEndAt(),
                c.getQuotaTotal(),
                c.getQuotaPerUser(),
                c.getMinOrderValueScoin(),
                c.getApplicableServiceIds(),
                c.getApplicableMentorIds(),
                totalRedemptions,
                activeRedemptions,
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }

    private Specification<Coupon> buildSpecification(AdminCouponListRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), request.getStatus()));
            }
            if (request.getDiscountType() != null) {
                predicates.add(cb.equal(root.get("discountType"), request.getDiscountType()));
            }
            if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
                String kw = "%" + request.getKeyword().trim().toLowerCase(Locale.ROOT) + "%";
                Predicate codeLike = cb.like(cb.lower(root.get("code")), kw);
                Predicate titleLike = cb.like(cb.lower(root.get("title")), kw);
                predicates.add(cb.or(codeLike, titleLike));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
