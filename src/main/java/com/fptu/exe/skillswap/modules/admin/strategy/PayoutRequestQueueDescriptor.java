package com.fptu.exe.skillswap.modules.admin.strategy;

import com.fptu.exe.skillswap.modules.admin.domain.AdminCaseType;
import com.fptu.exe.skillswap.modules.admin.domain.AdminQueueKey;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PayoutRequestQueueDescriptor implements AdminQueueDescriptor {

    @Override
    public boolean supports(AdminQueueKey queueKey) {
        return queueKey == AdminQueueKey.PAYOUT_REQUESTS_REQUESTED;
    }

    @Override
    public AdminCaseType resolveCaseType(AdminQueueKey queueKey) {
        return AdminCaseType.PAYOUT_REQUEST;
    }

    @Override
    public String resolveSeverity(AdminQueueKey queueKey) {
        return "medium";
    }

    @Override
    public String buildDetailPath(AdminQueueKey queueKey, String detailRefId) {
        return "/api/admin/payout-requests/" + detailRefId;
    }

    @Override
    public List<String> getAvailableActions(AdminQueueKey queueKey) {
        return List.of("VIEW_DETAIL", "ASSIGN_TO_ME");
    }
}
