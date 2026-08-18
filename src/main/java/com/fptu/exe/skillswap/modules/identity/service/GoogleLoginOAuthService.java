package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.infrastructure.config.GoogleApiProperties;
import com.fptu.exe.skillswap.modules.identity.dto.request.GoogleLoginRequest;
import com.fptu.exe.skillswap.modules.identity.dto.response.GoogleAuthorizationContextResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class GoogleLoginOAuthService {

    private final GoogleCalendarApiClient googleApiClient;
    private final GoogleAuthService googleAuthService;
    private final GoogleOAuthStateService googleOAuthStateService;
    private final GoogleApiProperties googleApiProperties;

    public GoogleAuthorizationContextResponse issueAuthorizationContext(
            String redirectUri,
            String codeChallenge
    ) {
        validateLoginRedirectUri(redirectUri);
        return googleOAuthStateService.issueLogin(redirectUri, codeChallenge);
    }

    public GoogleAuthService.GoogleUserInfo resolveUserInfo(GoogleLoginRequest request) {
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu dữ liệu đăng nhập Google");
        }
        if (!StringUtils.hasText(request.getAuthorizationCode())
                || !StringUtils.hasText(request.getRedirectUri())
                || !StringUtils.hasText(request.getCodeVerifier())
                || !StringUtils.hasText(request.getState())) {
            throw new BaseException(ErrorCode.BAD_REQUEST,
                    "Authorization code, redirect URI, PKCE verifier và state là bắt buộc");
        }

        validateLoginRedirectUri(request.getRedirectUri());
        googleOAuthStateService.consumeLogin(
                request.getState(),
                request.getRedirectUri(),
                request.getCodeVerifier()
        );
        GoogleCalendarApiClient.GoogleTokenResponse tokenResponse =
                googleApiClient.exchangeAuthorizationCode(
                        request.getAuthorizationCode(),
                        request.getRedirectUri(),
                        request.getCodeVerifier()
                );
        if (StringUtils.hasText(tokenResponse.idToken())) {
            return googleAuthService.verifyToken(tokenResponse.idToken());
        }

        GoogleCalendarApiClient.GoogleUserInfoResponse userInfo =
                googleApiClient.fetchUserInfo(tokenResponse.accessToken());
        if (!userInfo.emailVerified()) {
            throw new BaseException(ErrorCode.OAUTH_VERIFICATION_FAILED, "Email Google chưa được xác thực");
        }
        return googleAuthService.fromOpenIdProfile(
                userInfo.subject(),
                userInfo.email(),
                userInfo.name(),
                userInfo.picture(),
                true
        );
    }

    private void validateLoginRedirectUri(String redirectUri) {
        String configuredUris = googleApiProperties.getLoginRedirectUris();
        if (!StringUtils.hasText(configuredUris)) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR,
                    "GOOGLE_LOGIN_REDIRECT_URIS chưa được cấu hình");
        }
        boolean allowed = StringUtils.hasText(redirectUri)
                && Arrays.stream(configuredUris.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .anyMatch(redirectUri.trim()::equals);
        if (!allowed) {
            throw new BaseException(ErrorCode.BAD_REQUEST,
                    "redirectUri không nằm trong danh sách callback đăng nhập Google được phép");
        }
    }
}
