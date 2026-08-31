package com.fptu.exe.skillswap.modules.booking.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "booking_engagement_deliveries", uniqueConstraints = @UniqueConstraint(name = "uq_booking_engagement_delivery", columnNames = {"booking_id", "recipient_user_id", "delivery_type"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BookingEngagementDelivery {
    @Id @GeneratedUuidV7 private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "booking_id") private Booking booking;
    @Column(name = "recipient_user_id", nullable = false) private UUID recipientUserId;
    @Enumerated(EnumType.STRING) @Column(name = "delivery_type", nullable = false, length = 40) private BookingEngagementDeliveryType deliveryType;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = DateTimeUtil.now(); }
}
