package com.fptu.exe.skillswap.modules.booking.repository;

import com.fptu.exe.skillswap.modules.booking.domain.BookingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BookingEventRepository extends JpaRepository<BookingEvent, UUID> {
}
