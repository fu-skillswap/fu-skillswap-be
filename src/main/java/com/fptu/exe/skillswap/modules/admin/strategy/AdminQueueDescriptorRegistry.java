package com.fptu.exe.skillswap.modules.admin.strategy;

import com.fptu.exe.skillswap.modules.admin.domain.AdminCaseType;
import com.fptu.exe.skillswap.modules.admin.domain.AdminQueueKey;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AdminQueueDescriptorRegistry {

    private final List<AdminQueueDescriptor> descriptors;

    public AdminQueueDescriptorRegistry(List<AdminQueueDescriptor> descriptorList) {
        this.descriptors = descriptorList != null ? new ArrayList<>(descriptorList) : new ArrayList<>();
    }

    public AdminQueueDescriptor resolveDescriptor(AdminQueueKey queueKey) {
        if (queueKey == null) {
            return null;
        }
        for (AdminQueueDescriptor descriptor : descriptors) {
            if (descriptor.supports(queueKey)) {
                return descriptor;
            }
        }
        return null;
    }

    public AdminCaseType resolveCaseType(AdminQueueKey queueKey) {
        AdminQueueDescriptor descriptor = resolveDescriptor(queueKey);
        return descriptor != null ? descriptor.resolveCaseType(queueKey) : null;
    }

    public String resolveSeverity(AdminQueueKey queueKey) {
        AdminQueueDescriptor descriptor = resolveDescriptor(queueKey);
        return descriptor != null ? descriptor.resolveSeverity(queueKey) : "medium";
    }

    public String buildDetailPath(AdminQueueKey queueKey, String detailRefId) {
        AdminQueueDescriptor descriptor = resolveDescriptor(queueKey);
        return descriptor != null ? descriptor.buildDetailPath(queueKey, detailRefId) : "";
    }

    public List<String> availableActions(AdminQueueKey queueKey) {
        AdminQueueDescriptor descriptor = resolveDescriptor(queueKey);
        return descriptor != null ? descriptor.getAvailableActions(queueKey) : List.of("VIEW_DETAIL", "ASSIGN_TO_ME");
    }
}
