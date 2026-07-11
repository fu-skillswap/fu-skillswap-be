package com.fptu.exe.skillswap.modules.booking.event;

import com.fptu.exe.skillswap.modules.booking.service.BookingService;
import com.fptu.exe.skillswap.modules.mentor.event.MentorAvailabilityChangedEvent;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import com.fptu.exe.skillswap.shared.util.UuidUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class MentorAvailabilityChangedBookingListenerTest {

    @Mock
    private BookingService bookingService;

    @Test
    void listener_rejectsPendingBookings_whenMentorTurnsUnavailable() {
        MentorAvailabilityChangedBookingListener listener = new MentorAvailabilityChangedBookingListener(bookingService);
        MentorAvailabilityChangedEvent event = event(false);

        listener.onMentorAvailabilityChanged(event);

        verify(bookingService).rejectAllPendingBookingsForMentor(
                event.mentorUserId(),
                "Mentor đã chuyển sang trạng thái không nhận lịch"
        );
    }

    @Test
    void listener_ignoresAvailabilityChange_whenMentorBecomesAvailable() {
        MentorAvailabilityChangedBookingListener listener = new MentorAvailabilityChangedBookingListener(bookingService);
        MentorAvailabilityChangedEvent event = event(true);

        listener.onMentorAvailabilityChanged(event);

        verify(bookingService, never()).rejectAllPendingBookingsForMentor(event.mentorUserId(), "Mentor đã chuyển sang trạng thái không nhận lịch");
    }

    @Test
    void listener_logsStructuredErrorAndDoesNotThrow_whenBookingSideEffectFails(CapturedOutput output) {
        MentorAvailabilityChangedBookingListener listener = new MentorAvailabilityChangedBookingListener(bookingService);
        MentorAvailabilityChangedEvent event = event(false);
        doThrow(new IllegalStateException("boom")).when(bookingService)
                .rejectAllPendingBookingsForMentor(event.mentorUserId(), "Mentor đã chuyển sang trạng thái không nhận lịch");

        assertDoesNotThrow(() -> listener.onMentorAvailabilityChanged(event));

        String logs = output.getOut() + output.getErr();
        org.junit.jupiter.api.Assertions.assertTrue(logs.contains("\"eventType\":\"MentorAvailabilityChangedEvent\""));
        org.junit.jupiter.api.Assertions.assertTrue(logs.contains("\"producerDomain\":\"mentor\""));
        org.junit.jupiter.api.Assertions.assertTrue(logs.contains("\"consumerDomain\":\"booking\""));
        org.junit.jupiter.api.Assertions.assertTrue(logs.contains("\"mentorUserId\":\"" + event.mentorUserId() + "\""));
        org.junit.jupiter.api.Assertions.assertTrue(logs.contains("\"errorSummary\":\"boom\""));
    }

    private MentorAvailabilityChangedEvent event(boolean currentAvailability) {
        return new MentorAvailabilityChangedEvent(
                UuidUtil.generateUuidV7(),
                UuidUtil.generateUuidV7(),
                UuidUtil.generateUuidV7(),
                !currentAvailability,
                currentAvailability,
                DateTimeUtil.now()
        );
    }
}
