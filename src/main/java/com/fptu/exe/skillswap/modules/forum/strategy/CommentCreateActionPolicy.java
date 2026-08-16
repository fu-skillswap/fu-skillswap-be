package com.fptu.exe.skillswap.modules.forum.strategy;

import com.fptu.exe.skillswap.modules.forum.domain.ForumActionType;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CommentCreateActionPolicy implements ForumActionPolicy {

    @Override
    public ForumActionType getActionType() {
        return ForumActionType.CREATE_COMMENT;
    }

    @Override
    public int getLimit() {
        return 20;
    }

    @Override
    public Duration getWindow() {
        return Duration.ofMinutes(10);
    }

    @Override
    public String getRateLimitMessage() {
        return "Bạn bình luận quá nhanh, vui lòng thử lại sau";
    }
}
