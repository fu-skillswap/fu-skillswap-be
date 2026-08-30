package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingCompletionOutcome;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.port.BookingQueryPort;
import com.fptu.exe.skillswap.modules.chat.domain.*;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationBookingLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Single booking-owned policy boundary for direct-chat permissions. */
@Component
@RequiredArgsConstructor
public class BookingChatAccessPolicy {

    private final ConversationBookingLinkRepository linkRepository;
    private final BookingQueryPort bookingQueryPort;

    public Access resolve(UUID conversationId, ConversationStatus conversationStatus, LocalDateTime now) {
        var links = linkRepository.findByConversationId(conversationId);
        if (conversationStatus == ConversationStatus.LOCKED) {
            return Access.readOnly(ChatReadOnlyReason.ADMIN_LOCKED);
        }
        if (links.isEmpty() || bookingQueryPort == null) {
            return Access.readOnly(ChatReadOnlyReason.NO_EFFECTIVE_BOOKING);
        }

        List<UUID> bookingIds = links.stream().map(ConversationBookingLink::getBookingId).toList();
        List<Booking> bookings = bookingQueryPort.findBookingsByIds(bookingIds);

        boolean underReview = bookings.stream().anyMatch(b -> b.getStatus() == BookingStatus.UNDER_REVIEW);
        if (underReview) {
            return Access.readOnly(ChatReadOnlyReason.UNDER_REVIEW);
        }
        boolean permanent = bookings.stream().anyMatch(this::hasPermanentEntitlement);
        if (permanent) {
            return Access.open(null, true);
        }
        LocalDateTime deadline = bookings.stream()
                .filter(this::isEffective)
                .filter(b -> !b.isMaintainPostSessionChatSnapshot())
                .map(Booking::getSelectedEndTime).filter(java.util.Objects::nonNull)
                .map(end -> end.plusHours(24)).max(LocalDateTime::compareTo).orElse(null);
        if (deadline != null && now.isBefore(deadline)) {
            return Access.open(deadline, false);
        }
        return Access.readOnly(deadline == null ? ChatReadOnlyReason.NO_EFFECTIVE_BOOKING : ChatReadOnlyReason.CHAT_WINDOW_EXPIRED);
    }

    private boolean isEffective(Booking b) {
        return b.getStatus() == BookingStatus.PAID || b.getStatus() == BookingStatus.AWAITING_MENTOR_COMPLETION
                || b.getStatus() == BookingStatus.AWAITING_MENTEE_CONFIRMATION || b.getStatus() == BookingStatus.COMPLETED;
    }

    private boolean hasPermanentEntitlement(Booking b) {
        return b.isMaintainPostSessionChatSnapshot()
                && (b.getCompletionOutcome() == BookingCompletionOutcome.USER_CONFIRMED || b.getCompletionOutcome() == BookingCompletionOutcome.AUTO_CLOSED);
    }

    public record Access(
            ChatMessagingAccess messagingAccess,
            boolean canSendMessages,
            boolean canUploadAttachments,
            boolean canDownloadAttachments,
            ChatReadOnlyReason readOnlyReason,
            LocalDateTime messagingWindowEndsAt,
            boolean postSessionChatPermanent
    ) {
        public static Access open(LocalDateTime deadline, boolean permanent) {
            return new Access(ChatMessagingAccess.OPEN, true, true, true, null, deadline, permanent);
        }

        public static Access readOnly(ChatReadOnlyReason reason) {
            return new Access(ChatMessagingAccess.READ_ONLY, false, false, true, reason, null, false);
        }
    }
}
