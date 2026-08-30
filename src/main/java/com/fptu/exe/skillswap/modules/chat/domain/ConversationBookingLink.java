package com.fptu.exe.skillswap.modules.chat.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "conversation_booking_links", indexes = {
    @Index(name = "idx_conv_booking_links_conv", columnList = "conversation_id"),
    @Index(name = "idx_conv_booking_links_booking", columnList = "booking_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationBookingLink {
    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt;

    @PrePersist
    void onCreate() {
        if (linkedAt == null) {
            linkedAt = DateTimeUtil.now();
        }
    }
}
