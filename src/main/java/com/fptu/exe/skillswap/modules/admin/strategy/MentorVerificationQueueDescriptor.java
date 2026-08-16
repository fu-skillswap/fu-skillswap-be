package com.fptu.exe.skillswap.modules.admin.strategy;

import com.fptu.exe.skillswap.modules.admin.domain.AdminCaseType;
import com.fptu.exe.skillswap.modules.admin.domain.AdminQueueKey;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MentorVerificationQueueDescriptor implements AdminQueueDescriptor {

    @Override
    public boolean supports(AdminQueueKey queueKey) {
        return queueKey == AdminQueueKey.MENTOR_VERIFICATION_PENDING_REVIEW;
    }

    @Override
    public AdminCaseType resolveCaseType(AdminQueueKey queueKey) {
        return AdminCaseType.MENTOR_VERIFICATION_REQUEST;
    }

    @Override
    public String resolveSeverity(AdminQueueKey queueKey) {
        return "high";
    }

    @Override
    public String buildDetailPath(AdminQueueKey queueKey, String detailRefId) {
        return "/api/admin/mentor-verification/requests/" + detailRefId;
    }

    @Override
    public List<String> getAvailableActions(AdminQueueKey queueKey) {
        return List.of("VIEW_DETAIL", "ASSIGN_TO_ME");
    }
}
