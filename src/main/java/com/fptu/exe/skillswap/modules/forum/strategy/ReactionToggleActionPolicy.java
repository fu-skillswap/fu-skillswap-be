package com.fptu.exe.skillswap.modules.forum.strategy;

import com.fptu.exe.skillswap.modules.forum.domain.ForumActionType;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ReactionToggleActionPolicy implements ForumActionPolicy {

    @Override
    public ForumActionType getActionType() {
        return ForumActionType.TOGGLE_REACTION;
    }

    @Override
    public int getLimit() {
        return 60;
    }

    @Override
    public Duration getWindow() {
        return Duration.ofMinutes(10);
    }

    @Override
    public String getRateLimitMessage() {
        return "Bạn đang thả hoặc bỏ reaction quá nhanh, vui lòng thử lại sau";
    }
}
