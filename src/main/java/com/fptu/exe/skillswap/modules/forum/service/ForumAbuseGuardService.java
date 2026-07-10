package com.fptu.exe.skillswap.modules.forum.service;

import com.fptu.exe.skillswap.modules.forum.domain.ForumActionType;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class ForumAbuseGuardService {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    public void checkAndLog(User user, ForumActionType actionType) {
        String key = user.getId().toString() + "_" + actionType.name();
        Bucket bucket = buckets.get(key, k -> createNewBucket(actionType));
        if (bucket != null && !bucket.tryConsume(1)) {
            throw new BaseException(ErrorCode.TOO_MANY_REQUESTS, buildMessage(actionType));
        }
    }

    private Bucket createNewBucket(ForumActionType actionType) {
        long capacity = switch (actionType) {
            case CREATE_POST -> 5;
            case CREATE_COMMENT -> 20;
            case CREATE_REPORT -> 10;
            case TOGGLE_REACTION -> 60;
        };
        Duration refillPeriod = switch (actionType) {
            case CREATE_POST -> Duration.ofMinutes(10);
            case CREATE_COMMENT -> Duration.ofMinutes(10);
            case CREATE_REPORT -> Duration.ofMinutes(30);
            case TOGGLE_REACTION -> Duration.ofMinutes(10);
        };
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, refillPeriod)
                .build();
        return Bucket.builder().addLimit(limit).build();
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
