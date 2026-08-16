package com.fptu.exe.skillswap.modules.admin.strategy;

import com.fptu.exe.skillswap.modules.admin.domain.AdminCaseType;
import com.fptu.exe.skillswap.modules.admin.domain.AdminQueueKey;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BookingQueueDescriptor implements AdminQueueDescriptor {

    @Override
    public boolean supports(AdminQueueKey queueKey) {
        return queueKey == AdminQueueKey.BOOKING_UNDER_REVIEW
                || queueKey == AdminQueueKey.BOOKINGS_ACCEPTED_AWAITING_PAYMENT;
    }

    @Override
    public AdminCaseType resolveCaseType(AdminQueueKey queueKey) {
        return AdminCaseType.BOOKING;
    }

    @Override
    public String resolveSeverity(AdminQueueKey queueKey) {
        return queueKey == AdminQueueKey.BOOKING_UNDER_REVIEW ? "high" : "low";
    }

    @Override
    public String buildDetailPath(AdminQueueKey queueKey, String detailRefId) {
        return "/api/admin/bookings/" + detailRefId;
    }

    @Override
    public List<String> getAvailableActions(AdminQueueKey queueKey) {
        return List.of("VIEW_DETAIL", "ASSIGN_TO_ME");
    }
}
