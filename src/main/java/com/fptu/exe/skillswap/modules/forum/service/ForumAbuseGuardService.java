package com.fptu.exe.skillswap.modules.forum.service;

import com.fptu.exe.skillswap.modules.forum.domain.ForumActionType;
import com.fptu.exe.skillswap.modules.forum.strategy.ForumActionPolicy;
import com.fptu.exe.skillswap.modules.forum.strategy.ForumActionPolicyRegistry;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.shared.ratelimit.InMemoryRateLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class ForumAbuseGuardService {

    private final InMemoryRateLimitService rateLimitService;
    private final ForumActionPolicyRegistry forumActionPolicyRegistry;

    public void checkAndLog(User user, ForumActionType actionType) {
        String key = "forum_abuse:" + user.getId().toString() + "_" + actionType.name();

        ForumActionPolicy policy = forumActionPolicyRegistry != null ? forumActionPolicyRegistry.getPolicy(actionType) : null;
        int limit = policy != null ? policy.getLimit() : 10;
        Duration window = policy != null ? policy.getWindow() : Duration.ofMinutes(10);
        String message = policy != null ? policy.getRateLimitMessage() : "Thao tác quá nhanh, vui lòng thử lại sau";

        rateLimitService.check(com.fptu.exe.skillswap.shared.ratelimit.RateLimitScope.BUSINESS, key, limit, window, message);
    }
}
