package com.fptu.exe.skillswap.modules.forum.service;

import com.fptu.exe.skillswap.modules.forum.domain.ForumActionLog;
import com.fptu.exe.skillswap.modules.forum.domain.ForumActionType;
import com.fptu.exe.skillswap.modules.forum.repository.ForumActionLogRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ForumAbuseGuardService {

    private final ForumActionLogRepository forumActionLogRepository;

    @Transactional
    public void checkAndLog(User user, ForumActionType actionType) {
        LocalDateTime now = DateTimeUtil.now();
        LocalDateTime cutoff = switch (actionType) {
            case CREATE_POST -> now.minusMinutes(10);
            case CREATE_COMMENT -> now.minusMinutes(10);
            case CREATE_REPORT -> now.minusMinutes(30);
            case TOGGLE_REACTION -> now.minusMinutes(10);
        };
        long currentCount = forumActionLogRepository.countByUserIdAndActionTypeAndCreatedAtAfter(user.getId(), actionType, cutoff);
        long limit = switch (actionType) {
            case CREATE_POST -> 5;
            case CREATE_COMMENT -> 20;
            case CREATE_REPORT -> 10;
            case TOGGLE_REACTION -> 60;
        };
        if (currentCount >= limit) {
            throw new BaseException(ErrorCode.TOO_MANY_REQUESTS, buildMessage(actionType));
        }
        forumActionLogRepository.save(ForumActionLog.builder()
                .user(user)
                .actionType(actionType)
                .createdAt(now)
                .build());
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
