package com.fptu.exe.skillswap.modules.forum.strategy;

import com.fptu.exe.skillswap.modules.forum.domain.ForumActionType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ForumActionPolicyRegistry {

    private final Map<ForumActionType, ForumActionPolicy> policies = new EnumMap<>(ForumActionType.class);

    public ForumActionPolicyRegistry(List<ForumActionPolicy> policyList) {
        if (policyList != null) {
            for (ForumActionPolicy policy : policyList) {
                if (policy.getActionType() != null) {
                    policies.put(policy.getActionType(), policy);
                }
            }
        }
    }

    public ForumActionPolicy getPolicy(ForumActionType actionType) {
        return policies.get(actionType);
    }
}
