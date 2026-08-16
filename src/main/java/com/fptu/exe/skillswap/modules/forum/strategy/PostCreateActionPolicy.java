package com.fptu.exe.skillswap.modules.forum.strategy;

import com.fptu.exe.skillswap.modules.forum.domain.ForumActionType;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class PostCreateActionPolicy implements ForumActionPolicy {

    @Override
    public ForumActionType getActionType() {
        return ForumActionType.CREATE_POST;
    }

    @Override
    public int getLimit() {
        return 5;
    }

    @Override
    public Duration getWindow() {
        return Duration.ofMinutes(10);
    }

    @Override
    public String getRateLimitMessage() {
        return "Bạn đăng bài quá nhanh, vui lòng thử lại sau vài phút";
    }
}
