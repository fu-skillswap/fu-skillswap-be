package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.infrastructure.config.JwtProperties;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleAuthService {

    private final JwtProperties jwtProperties;
    private volatile GoogleIdTokenVerifier verifier;

    public GoogleUserInfo verifyToken(String idToken) {
        if (!StringUtils.hasText(idToken)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã xác thực Google không được để trống");
        }

        try {
            GoogleIdToken googleIdToken = getVerifier().verify(idToken);
            if (googleIdToken == null || googleIdToken.getPayload() == null) {
                throw new BaseException(ErrorCode.OAUTH_VERIFICATION_FAILED, "Xác thực Google ID Token thất bại");
            }

            String expectedClientId = jwtProperties.getGoogle().getClientId();
            if (!StringUtils.hasText(expectedClientId)) {
                throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Thiếu cấu hình GOOGLE_CLIENT_ID cho đăng nhập Google");
            }
            GoogleIdToken.Payload payload = googleIdToken.getPayload();
            if (!expectedClientId.equals(payload.getAudience())) {
                log.error("Google Token Client ID mismatch. Expected: {}, Got: {}", expectedClientId, payload.getAudience());
                throw new BaseException(ErrorCode.OAUTH_VERIFICATION_FAILED, "Không khớp Client ID xác thực Google");
            }
            if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
                log.error("Google Token email is not verified for email: {}", payload.getEmail());
                throw new BaseException(ErrorCode.OAUTH_VERIFICATION_FAILED, "Email liên kết với tài khoản Google chưa được xác thực");
            }
            return toUserInfo(payload);
        } catch (BaseException e) {
            throw e;
        } catch (GeneralSecurityException | IOException e) {
            log.error("Error verifying Google ID token: {}", e.getMessage(), e);
            throw new BaseException(
                    ErrorCode.OAUTH_VERIFICATION_FAILED,
                    "Không thể xác thực đăng nhập Google do kết nối tới hệ thống Google tạm thời không ổn định"
            );
        }
    }

    private GoogleIdTokenVerifier getVerifier() throws GeneralSecurityException, IOException {
        GoogleIdTokenVerifier current = verifier;
        if (current == null) {
            synchronized (this) {
                current = verifier;
                if (current == null) {
                    String clientId = jwtProperties.getGoogle().getClientId();
                    if (!StringUtils.hasText(clientId)) {
                        throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Thiếu cấu hình GOOGLE_CLIENT_ID cho đăng nhập Google");
                    }
                    current = new GoogleIdTokenVerifier.Builder(
                            GoogleNetHttpTransport.newTrustedTransport(),
                            GsonFactory.getDefaultInstance()
                    )
                            .setAudience(Collections.singletonList(clientId))
                            .build();
                    verifier = current;
                }
            }
        }
        return current;
    }

    private GoogleUserInfo toUserInfo(GoogleIdToken.Payload payload) {
        return fromOpenIdProfile(
                payload.getSubject() != null ? String.valueOf(payload.getSubject()) : null,
                payload.getEmail() != null ? String.valueOf(payload.getEmail()) : null,
                payload.get("name") != null ? String.valueOf(payload.get("name")) : null,
                payload.get("picture") != null ? String.valueOf(payload.get("picture")) : null,
                Boolean.TRUE.equals(payload.getEmailVerified())
        );
    }

    public GoogleUserInfo fromOpenIdProfile(String sub, String email, String name, String picture, boolean emailVerified) {
        GoogleUserInfo info = new GoogleUserInfo();
        info.setSub(sub);
        info.setEmail(email);
        info.setEmail_verified(String.valueOf(emailVerified));
        info.setName(name);
        info.setPicture(picture);
        return info;
    }

    @Getter
    @Setter
    public static class GoogleUserInfo {
        private String iss;
        private String sub;
        private String aud;
        private String email;
        private String email_verified;
        private String name;
        private String picture;
    }
}
