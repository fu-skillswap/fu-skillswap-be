package com.fptu.exe.skillswap.modules.conversation.repository;

import com.fptu.exe.skillswap.modules.conversation.domain.ConversationBookingLink;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ConversationBookingLinkRepository extends JpaRepository<ConversationBookingLink, UUID> {
    boolean existsByBookingId(UUID bookingId);
    Optional<ConversationBookingLink> findByBookingId(UUID bookingId);
    List<ConversationBookingLink> findByConversationId(UUID conversationId);
}
