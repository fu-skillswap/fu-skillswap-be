package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.modules.payment.domain.PaymentAttempt;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentTargetType;
import com.fptu.exe.skillswap.modules.payment.dto.response.PaymentCheckoutResponse;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentAttemptRepository;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentOrderQueryService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentResponseMapper paymentResponseMapper;

    @Transactional(readOnly = true)
    public PaymentCheckoutResponse getByTarget(UUID currentUserId, PaymentTargetType targetType, UUID targetId) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        PaymentOrder order = paymentOrderRepository.findByTargetTypeAndTargetId(targetType, targetId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy payment order"));
        if (!currentUserId.equals(order.getPayerUserId()) && !currentUserId.equals(order.getMentorUserId())) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Không có quyền xem payment order này");
        }

        PaymentAttempt latestAttempt = paymentAttemptRepository
                .findFirstByPaymentOrderIdOrderByAttemptNoDesc(order.getId())
                .orElse(null);
        return paymentResponseMapper.toResponse(order, latestAttempt);
    }

    @Transactional(readOnly = true)
    public void assertAccess(UUID currentUserId, PaymentTargetType targetType, UUID targetId) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        PaymentOrder order = paymentOrderRepository.findByTargetTypeAndTargetId(targetType, targetId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy payment order"));
        if (!currentUserId.equals(order.getPayerUserId()) && !currentUserId.equals(order.getMentorUserId())) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Không có quyền thao tác payment order này");
        }
    }
}
