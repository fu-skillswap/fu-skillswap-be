package com.fptu.exe.skillswap.modules.booking.repository;

import com.fptu.exe.skillswap.modules.booking.domain.BookingEngagementDelivery;
import com.fptu.exe.skillswap.modules.booking.domain.BookingEngagementDeliveryType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface BookingEngagementDeliveryRepository extends JpaRepository<BookingEngagementDelivery, UUID> {
    boolean existsByBookingIdAndRecipientUserIdAndDeliveryType(UUID bookingId, UUID recipientUserId, BookingEngagementDeliveryType deliveryType);
}
