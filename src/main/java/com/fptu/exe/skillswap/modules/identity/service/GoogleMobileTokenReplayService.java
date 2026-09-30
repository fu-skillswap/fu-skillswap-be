package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.modules.identity.repository.GoogleMobileTokenReplayRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.DateTimeException;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class GoogleMobileTokenReplayService {

    private final GoogleMobileTokenReplayRepository replayRepository;

    public void claim(String googleSubject, Long issuedAt, Long expiration) {
        if (!StringUtils.hasText(googleSubject) || issuedAt == null || expiration == null
                || expiration <= issuedAt) {
            throw invalidToken();
        }

        final Instant expiresAt;
        try {
            expiresAt = Instant.ofEpochSecond(expiration);
        } catch (DateTimeException ex) {
            throw invalidToken();
        }

        if (!replayRepository.claim(googleSubject, issuedAt, expiresAt)) {
            throw new BaseException(
                    ErrorCode.OAUTH_VERIFICATION_FAILED,
                    "Google ID Token đã được sử dụng"
            );
        }
    }

    private BaseException invalidToken() {
        return new BaseException(
                ErrorCode.OAUTH_VERIFICATION_FAILED,
                "Google ID Token thiếu thông tin thời hạn hợp lệ"
        );
    }
}
