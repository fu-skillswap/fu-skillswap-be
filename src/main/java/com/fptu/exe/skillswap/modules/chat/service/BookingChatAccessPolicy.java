package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.modules.booking.port.BookingChatAccessPort;
import com.fptu.exe.skillswap.modules.chat.domain.ChatMessagingAccess;
import com.fptu.exe.skillswap.modules.chat.domain.ChatReadOnlyReason;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationBookingLink;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationStatus;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationBookingLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Chat-owned policy boundary for booking-linked direct-chat permissions. */
@Component
@RequiredArgsConstructor
public class BookingChatAccessPolicy {

    private final ConversationBookingLinkRepository linkRepository;
    private final BookingChatAccessPort chatAccessSnapshotPort;

    public Access resolve(UUID conversationId, ConversationStatus conversationStatus, LocalDateTime now) {
        var links = linkRepository.findByConversationId(conversationId);
        if (conversationStatus == ConversationStatus.LOCKED) return Access.readOnly(ChatReadOnlyReason.ADMIN_LOCKED);
        List<BookingChatAccessPort.ChatAccessSnapshot> bookings = chatAccessSnapshotPort.findChatAccessSnapshots(
                links.stream().map(ConversationBookingLink::getBookingId).toList());
        boolean underReview = bookings.stream().anyMatch(b -> "UNDER_REVIEW".equals(b.status()));
        if (underReview) return Access.readOnly(ChatReadOnlyReason.UNDER_REVIEW);
        boolean permanent = bookings.stream().anyMatch(this::hasPermanentEntitlement);
        if (permanent) return Access.open(null, true);
        LocalDateTime deadline = bookings.stream()
                .filter(this::isEffective)
                .filter(b -> !b.maintainPostSessionChat())
                .map(BookingChatAccessPort.ChatAccessSnapshot::selectedEndTime).filter(java.util.Objects::nonNull)
                .map(end -> end.plusHours(24)).max(LocalDateTime::compareTo).orElse(null);
        if (deadline != null && now.isBefore(deadline)) return Access.open(deadline, false);
        return Access.readOnly(deadline == null ? ChatReadOnlyReason.NO_EFFECTIVE_BOOKING : ChatReadOnlyReason.CHAT_WINDOW_EXPIRED);
    }

    private boolean isEffective(BookingChatAccessPort.ChatAccessSnapshot booking) {
        return "PAID".equals(booking.status()) || "AWAITING_MENTOR_COMPLETION".equals(booking.status())
                || "AWAITING_MENTEE_CONFIRMATION".equals(booking.status()) || "COMPLETED".equals(booking.status());
    }

    private boolean hasPermanentEntitlement(BookingChatAccessPort.ChatAccessSnapshot booking) {
        return booking.maintainPostSessionChat()
                && ("USER_CONFIRMED".equals(booking.completionOutcome()) || "AUTO_CLOSED".equals(booking.completionOutcome()));
    }

    public record Access(ChatMessagingAccess messagingAccess, boolean canSendMessages, boolean canUploadAttachments,
                         boolean canDownloadAttachments, ChatReadOnlyReason readOnlyReason,
                         LocalDateTime messagingWindowEndsAt, boolean postSessionChatPermanent) {
        public static Access open(LocalDateTime deadline, boolean permanent) { return new Access(ChatMessagingAccess.OPEN, true, true, true, null, deadline, permanent); }
        public static Access readOnly(ChatReadOnlyReason reason) { return new Access(ChatMessagingAccess.READ_ONLY, false, false, true, reason, null, false); }
    }
}
