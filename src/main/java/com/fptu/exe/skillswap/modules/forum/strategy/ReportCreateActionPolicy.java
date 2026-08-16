package com.fptu.exe.skillswap.modules.forum.strategy;

import com.fptu.exe.skillswap.modules.forum.domain.ForumActionType;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ReportCreateActionPolicy implements ForumActionPolicy {

    @Override
    public ForumActionType getActionType() {
        return ForumActionType.CREATE_REPORT;
    }

    @Override
    public int getLimit() {
        return 10;
    }

    @Override
    public Duration getWindow() {
        return Duration.ofMinutes(30);
    }

    @Override
    public String getRateLimitMessage() {
        return "Bạn đang gửi report quá nhanh, vui lòng thử lại sau";
    }
}
