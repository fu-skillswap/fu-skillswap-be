package com.fptu.exe.skillswap.modules.conversation.domain;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "conversation_booking_links")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ConversationBookingLink {
    @Id @GeneratedUuidV7 private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "conversation_id") private Conversation conversation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "booking_id") private Booking booking;
    @Column(name = "linked_at", nullable = false) private LocalDateTime linkedAt;
    @PrePersist void onCreate() { if (linkedAt == null) linkedAt = DateTimeUtil.now(); }
}
