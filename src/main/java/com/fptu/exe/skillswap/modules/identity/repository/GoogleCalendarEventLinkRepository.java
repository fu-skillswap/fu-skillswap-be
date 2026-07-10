package com.fptu.exe.skillswap.modules.identity.repository;

import com.fptu.exe.skillswap.modules.identity.domain.GoogleCalendarEventLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GoogleCalendarEventLinkRepository extends JpaRepository<GoogleCalendarEventLink, UUID> {

    Optional<GoogleCalendarEventLink> findByBookingId(UUID bookingId);
}
