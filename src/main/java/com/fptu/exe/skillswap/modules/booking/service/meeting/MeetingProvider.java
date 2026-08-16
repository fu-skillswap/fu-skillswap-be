package com.fptu.exe.skillswap.modules.booking.service.meeting;

import com.fptu.exe.skillswap.modules.booking.domain.MeetingPlatform;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.springframework.util.StringUtils;

import java.net.URI;

public interface MeetingProvider {

    MeetingPlatform getPlatform();

    boolean isOnlinePlatform();

    default void validateMeetingLink(String meetingLink) {
        if (!isOnlinePlatform()) {
            return;
        }
        if (!StringUtils.hasText(meetingLink)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "meetingLink là bắt buộc cho nền tảng trực tuyến");
        }
        try {
            URI uri = URI.create(meetingLink.trim());
            if (uri.getScheme() == null || (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https"))) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "meetingLink phải là URL hợp lệ (http/https)");
            }
        } catch (IllegalArgumentException ex) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "meetingLink không đúng định dạng URL");
        }
    }
}
