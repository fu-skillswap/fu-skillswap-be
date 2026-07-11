package com.fptu.exe.skillswap.modules.forum.service;

import com.fptu.exe.skillswap.modules.forum.domain.ForumActionType;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.shared.ratelimit.InMemoryRateLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class ForumAbuseGuardService {

    private final InMemoryRateLimitService rateLimitService;

    public void checkAndLog(User user, ForumActionType actionType) {
        String key = "forum_abuse:" + user.getId().toString() + "_" + actionType.name();
        
        int limit = switch (actionType) {
            case CREATE_POST -> 5;
            case CREATE_COMMENT -> 20;
            case CREATE_REPORT -> 10;
            case TOGGLE_REACTION -> 60;
        };
        
        Duration window = switch (actionType) {
            case CREATE_POST -> Duration.ofMinutes(10);
            case CREATE_COMMENT -> Duration.ofMinutes(10);
            case CREATE_REPORT -> Duration.ofMinutes(30);
            case TOGGLE_REACTION -> Duration.ofMinutes(10);
        };
        
        String message = buildMessage(actionType);
        
        rateLimitService.check(key, limit, window, message);
    }

    private String buildMessage(ForumActionType actionType) {
        return switch (actionType) {
            case CREATE_POST -> "Bạn đăng bài quá nhanh, vui lòng thử lại sau vài phút";
            case CREATE_COMMENT -> "Bạn bình luận quá nhanh, vui lòng thử lại sau";
            case CREATE_REPORT -> "Bạn đang gửi report quá nhanh, vui lòng thử lại sau";
            case TOGGLE_REACTION -> "Bạn đang thả hoặc bỏ reaction quá nhanh, vui lòng thử lại sau";
        };
    }
}
