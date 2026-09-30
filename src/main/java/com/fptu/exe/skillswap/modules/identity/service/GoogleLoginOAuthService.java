package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.modules.identity.dto.request.GoogleLoginRequest;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class GoogleLoginOAuthService {

    private final GoogleAuthService googleAuthService;
    private final GoogleLoginNonceService googleLoginNonceService;
    private final GoogleMobileTokenReplayService googleMobileTokenReplayService;

    public GoogleAuthService.GoogleUserInfo resolveUserInfo(GoogleLoginRequest request) {
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu dữ liệu đăng nhập Google");
        }
        if (!StringUtils.hasText(request.getCredential())
                || !StringUtils.hasText(request.getNonce())) {
            throw new BaseException(ErrorCode.BAD_REQUEST,
                    "Google credential và nonce là bắt buộc");
        }

        GoogleAuthService.GoogleUserInfo userInfo =
                googleAuthService.verifyToken(request.getCredential(), request.getNonce());
        googleLoginNonceService.consume(request.getNonce());
        return userInfo;
    }

    public GoogleAuthService.GoogleUserInfo resolveMobileUserInfo(String credential) {
        if (!StringUtils.hasText(credential)) {
            throw new BaseException(ErrorCode.BAD_REQUEST,
                    "Google credential là bắt buộc");
        }

        GoogleAuthService.GoogleMobileTokenVerification verified =
                googleAuthService.verifyMobileToken(credential);
        googleMobileTokenReplayService.claim(
                verified.userInfo().getSub(),
                verified.issuedAt(),
                verified.expiration()
        );
        return verified.userInfo();
    }
}
