package com.fptu.exe.skillswap.modules.payment.port;

import com.fptu.exe.skillswap.modules.payment.dto.response.ServicePricingPreviewResponse;
import com.fptu.exe.skillswap.modules.payment.port.dto.BookingPaymentSummaryDto;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface PaymentPort {
    void cancelUnpaidOrdersForBooking(UUID bookingId);
    void settleBooking(UUID bookingId);
    void releaseEscrowRefund(UUID bookingId, String reason);
    Optional<BookingPaymentSummaryDto> getPaymentSummaryForBooking(UUID bookingId);
    Map<UUID, BookingPaymentSummaryDto> getPaymentSummariesForBookings(Collection<UUID> bookingIds);
    ServicePricingPreviewResponse previewBookingPrice(UUID bookingId, UUID menteeUserId, String couponCode);
}
