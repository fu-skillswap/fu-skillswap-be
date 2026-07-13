package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingEvent;
import com.fptu.exe.skillswap.modules.booking.domain.BookingEventActorType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingEventType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.repository.BookingEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingEventService {
    private final BookingEventRepository bookingEventRepository;

    public void record(Booking booking, BookingEventType type, BookingStatus oldStatus,
                       BookingEventActorType actorType, UUID actorUserId, String metadataJson) {
        if (booking == null || booking.getId() == null) {
            return;
        }
        bookingEventRepository.save(BookingEvent.builder()
                .bookingId(booking.getId())
                .eventType(type)
                .actorType(actorType)
                .actorUserId(actorUserId)
                .oldStatus(oldStatus)
                .newStatus(booking.getStatus())
                .metadataJson(metadataJson)
                .build());
    }
}
