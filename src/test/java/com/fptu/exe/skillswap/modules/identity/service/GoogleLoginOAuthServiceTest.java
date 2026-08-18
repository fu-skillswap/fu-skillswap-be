package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.modules.identity.dto.request.GoogleLoginRequest;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleLoginOAuthServiceTest {

    @Mock private GoogleAuthService googleAuthService;
    @Mock private GoogleLoginNonceService nonceService;
    @InjectMocks private GoogleLoginOAuthService service;

    @Test
    void resolveUserInfo_shouldVerifySignedTokenBeforeConsumingNonce() {
        GoogleLoginRequest request = new GoogleLoginRequest("id-token", "nonce");
        GoogleAuthService.GoogleUserInfo expected = new GoogleAuthService.GoogleUserInfo();
        expected.setSub("google-sub");
        expected.setEmail("user@fpt.edu.vn");
        when(googleAuthService.verifyToken("id-token", "nonce")).thenReturn(expected);

        var result = service.resolveUserInfo(request);

        assertEquals(expected, result);
        var order = inOrder(googleAuthService, nonceService);
        order.verify(googleAuthService).verifyToken("id-token", "nonce");
        order.verify(nonceService).consume("nonce");
    }

    @Test
    void resolveUserInfo_invalidToken_shouldNotBurnNonce() {
        GoogleLoginRequest request = new GoogleLoginRequest("invalid", "nonce");
        when(googleAuthService.verifyToken("invalid", "nonce")).thenThrow(
                new BaseException(ErrorCode.OAUTH_VERIFICATION_FAILED, "Token không hợp lệ")
        );

        assertThrows(BaseException.class, () -> service.resolveUserInfo(request));

        verify(nonceService, never()).consume("nonce");
    }
}
