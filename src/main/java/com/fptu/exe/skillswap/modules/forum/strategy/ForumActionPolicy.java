package com.fptu.exe.skillswap.modules.forum.strategy;

import com.fptu.exe.skillswap.modules.forum.domain.ForumActionType;

import java.time.Duration;

public interface ForumActionPolicy {

    ForumActionType getActionType();

    int getLimit();

    Duration getWindow();

    String getRateLimitMessage();
}
