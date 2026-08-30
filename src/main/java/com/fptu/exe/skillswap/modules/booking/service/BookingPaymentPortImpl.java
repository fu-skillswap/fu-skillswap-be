package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionCommand;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionExecutor;
import com.fptu.exe.skillswap.modules.booking.port.BookingPaymentPort;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingPaymentPortImpl implements BookingPaymentPort {

    private final BookingRepository bookingRepository;

    @Override
    @Transactional
    public void markBookingPaid(UUID bookingId, UUID paymentOrderId) {
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null) return;

        BookingTransitionExecutor.apply(booking, BookingTransitionCommand.PAYMENT_CONFIRMED, DateTimeUtil.instantNow());
        bookingRepository.save(booking);
    }
}
