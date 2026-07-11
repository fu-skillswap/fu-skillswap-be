package com.fptu.exe.skillswap.modules.booking.event;

import com.fptu.exe.skillswap.modules.booking.service.BookingService;
import com.fptu.exe.skillswap.modules.mentor.event.MentorAvailabilityChangedEvent;
import com.fptu.exe.skillswap.shared.util.AuditLogJsonUtil;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class MentorAvailabilityChangedBookingListener {

    private static final String AUTO_REJECT_REASON = "Mentor đã chuyển sang trạng thái không nhận lịch";

    private final BookingService bookingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMentorAvailabilityChanged(MentorAvailabilityChangedEvent event) {
        if (event == null || event.mentorUserId() == null || event.currentAvailability()) {
            return;
        }

        try {
            bookingService.rejectAllPendingBookingsForMentor(event.mentorUserId(), AUTO_REJECT_REASON);
        } catch (Exception ex) {
            log.error(AuditLogJsonUtil.toJson(errorPayload(event, ex)), ex);
        }
    }

    private Map<String, Object> errorPayload(MentorAvailabilityChangedEvent event, Exception ex) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.eventId());
        payload.put("eventType", MentorAvailabilityChangedEvent.class.getSimpleName());
        payload.put("producerDomain", "mentor");
        payload.put("consumerDomain", "booking");
        payload.put("aggregateId", event.mentorProfileId());
        payload.put("mentorUserId", event.mentorUserId());
        payload.put("payloadSummary", "availability:" + event.previousAvailability() + "->" + event.currentAvailability());
        payload.put("errorSummary", summarize(ex));
        payload.put("occurredAt", DateTimeUtil.now().toString());
        return payload;
    }

    private String summarize(Exception ex) {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) {
            return "Unknown consumer failure";
        }
        String message = ex.getMessage().replace("\"", "'");
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
