package com.fptu.exe.skillswap.modules.chat.repository;

import com.fptu.exe.skillswap.modules.chat.domain.ConversationBookingLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationBookingLinkRepository extends JpaRepository<ConversationBookingLink, UUID> {
    List<ConversationBookingLink> findByConversationId(UUID conversationId);
    Optional<ConversationBookingLink> findFirstByBookingId(UUID bookingId);

    @Query("SELECT l FROM ConversationBookingLink l WHERE l.bookingId IN :bookingIds")
    List<ConversationBookingLink> findByBookingIdIn(@Param("bookingIds") Collection<UUID> bookingIds);
}
