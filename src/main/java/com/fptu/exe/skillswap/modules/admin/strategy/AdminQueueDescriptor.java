package com.fptu.exe.skillswap.modules.admin.strategy;

import com.fptu.exe.skillswap.modules.admin.domain.AdminCaseType;
import com.fptu.exe.skillswap.modules.admin.domain.AdminQueueKey;

import java.util.List;

public interface AdminQueueDescriptor {

    boolean supports(AdminQueueKey queueKey);

    AdminCaseType resolveCaseType(AdminQueueKey queueKey);

    String resolveSeverity(AdminQueueKey queueKey);

    String buildDetailPath(AdminQueueKey queueKey, String detailRefId);

    List<String> getAvailableActions(AdminQueueKey queueKey);
}
