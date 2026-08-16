package com.fptu.exe.skillswap.modules.admin.strategy;

import com.fptu.exe.skillswap.modules.admin.domain.AdminCaseType;
import com.fptu.exe.skillswap.modules.admin.domain.AdminQueueKey;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentOrdersFailedQueueDescriptor implements AdminQueueDescriptor {

    @Override
    public boolean supports(AdminQueueKey queueKey) {
        return queueKey == AdminQueueKey.PAYMENT_ORDERS_FAILED;
    }

    @Override
    public AdminCaseType resolveCaseType(AdminQueueKey queueKey) {
        return AdminCaseType.PAYMENT_ORDER;
    }

    @Override
    public String resolveSeverity(AdminQueueKey queueKey) {
        return "medium";
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
