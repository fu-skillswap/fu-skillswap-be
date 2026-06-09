package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.infrastructure.config.JwtProperties;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleAuthService {

    private final JwtProperties jwtProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    public GoogleUserInfo verifyToken(String idToken) {
        if (!StringUtils.hasText(idToken)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã xác thực Google không được để trống");
        }

        String url = "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken;
        try {
            GoogleUserInfo response = restTemplate.getForObject(url, GoogleUserInfo.class);
            if (response == null || response.getSub() == null) {
                throw new BaseException(ErrorCode.OAUTH_VERIFICATION_FAILED, "Xác thực Google ID Token thất bại");
            }

            String expectedClientId = jwtProperties.getGoogle().getClientId();
            if (!StringUtils.hasText(expectedClientId)) {
                throw new IllegalStateException("GOOGLE_CLIENT_ID is required for Google authentication");
            }

            if (!expectedClientId.equals(response.getAud())) {
                log.error("Google Token Client ID mismatch. Expected: {}, Got: {}", expectedClientId, response.getAud());
                throw new BaseException(ErrorCode.OAUTH_VERIFICATION_FAILED, "Không khớp Client ID xác thực Google");
            }

            return response;
        } catch (Exception e) {
            log.error("Error verifying Google ID token: {}", e.getMessage(), e);
            throw new BaseException(ErrorCode.OAUTH_VERIFICATION_FAILED, "Lỗi kết nối hệ thống Google: " + e.getMessage());
        }
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
