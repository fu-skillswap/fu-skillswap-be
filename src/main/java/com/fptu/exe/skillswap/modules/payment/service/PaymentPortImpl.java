package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentTargetType;
import com.fptu.exe.skillswap.modules.payment.dto.response.ServicePricingPreviewResponse;
import com.fptu.exe.skillswap.modules.payment.port.PaymentPort;
import com.fptu.exe.skillswap.modules.payment.port.dto.BookingPaymentSummaryDto;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentPortImpl implements PaymentPort {

    private final PaymentOrderService paymentOrderService;
    private final SettlementService settlementService;
    private final PaymentOrderRepository paymentOrderRepository;
    private final BookingPricingPreviewService bookingPricingPreviewService;

    @Override
    @Transactional
    public void cancelUnpaidOrdersForBooking(UUID bookingId) {
        if (bookingId != null) {
            paymentOrderService.cancelUnpaidOrdersForTarget(PaymentTargetType.BOOKING, bookingId, "BOOKING_CANCELLED");
        }
    }

    @Override
    @Transactional
    public void settleBooking(UUID bookingId) {
        if (bookingId != null) {
            settlementService.settleBooking(bookingId);
        }
    }

    @Override
    @Transactional
    public void releaseEscrowRefund(UUID bookingId, String reason) {
        if (bookingId != null) {
            settlementService.releaseEscrowRefund(bookingId, reason);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BookingPaymentSummaryDto> getPaymentSummaryForBooking(UUID bookingId) {
        if (bookingId == null) return Optional.empty();
        return paymentOrderRepository.findFirstByTargetTypeAndTargetIdOrderByCreatedAtDesc(PaymentTargetType.BOOKING, bookingId)
                .map(this::toSummaryDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, BookingPaymentSummaryDto> getPaymentSummariesForBookings(Collection<UUID> bookingIds) {
        if (bookingIds == null || bookingIds.isEmpty()) return Map.of();
        List<PaymentOrder> orders = paymentOrderRepository.findByTargetTypeAndTargetIdIn(PaymentTargetType.BOOKING, bookingIds);
        Map<UUID, BookingPaymentSummaryDto> result = new HashMap<>();
        for (PaymentOrder order : orders) {
            if (!result.containsKey(order.getTargetId())) {
                result.put(order.getTargetId(), toSummaryDto(order));
            }
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public ServicePricingPreviewResponse previewBookingPrice(UUID bookingId, UUID menteeUserId, String couponCode) {
        return bookingPricingPreviewService.previewBookingPricing(bookingId, menteeUserId, couponCode);
    }

    private BookingPaymentSummaryDto toSummaryDto(PaymentOrder order) {
        return BookingPaymentSummaryDto.builder()
                .paymentOrderId(order.getId())
                .orderCode(order.getOrderCode())
                .paymentStatus(order.getStatus() != null ? order.getStatus().name() : null)
                .settlementStatus(order.getSettlementStatus() != null ? order.getSettlementStatus().name() : null)
                .amountScoin(order.getFinalAmountScoin())
                .paidAt(order.getPaidAt())
                .checkoutUrl(order.getCheckoutUrl())
                .build();
    }
}
