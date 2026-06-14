package com.fptu.exe.skillswap.modules.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.modules.identity.dto.request.GoogleLoginRequest;
import com.fptu.exe.skillswap.modules.identity.dto.request.RefreshTokenRequest;
import com.fptu.exe.skillswap.modules.identity.dto.response.TokenResponse;
import com.fptu.exe.skillswap.modules.identity.service.IdentityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Validation Tests cho AuthController.
 *
 * <p>Xác nhận rằng:
 * <ul>
 *   <li>Các trường bắt buộc nếu trống / null → HTTP 400 với code VAL_3001</li>
 *   <li>Dữ liệu hợp lệ → HTTP 200</li>
 * </ul>
 *
 * <p>Dùng {@code @MockBean IdentityService} để cô lập tầng Controller + Validation,
 * không phụ thuộc vào DB hay Google OAuth thật.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("AuthController – Validation Tests")
class AuthControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IdentityService identityService;

    // ─────────────────────────────────────────────────────────────
    // POST /api/auth/google
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/google")
    class LoginWithGoogle {

        @Test
        @DisplayName("idToken rỗng (blank string) → 400 VAL_3001")
        void loginGoogle_idTokenBlank_shouldReturn400() throws Exception {
            GoogleLoginRequest request = new GoogleLoginRequest("");

            mockMvc.perform(post("/api/auth/google")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VAL_3001"))
                    .andExpect(jsonPath("$.message").value("idToken: Mã định danh Google không được để trống"));
        }

        @Test
        @DisplayName("idToken null (thiếu field trong JSON) → 400 VAL_3001")
        void loginGoogle_idTokenNull_shouldReturn400() throws Exception {
            // Gửi body không chứa field idToken
            String body = objectMapper.writeValueAsString(Map.of());

            mockMvc.perform(post("/api/auth/google")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VAL_3001"))
                    .andExpect(jsonPath("$.message").value("idToken: Mã định danh Google không được để trống"));
        }

        @Test
        @DisplayName("idToken hợp lệ → 200 SUCCESS_0200")
        void loginGoogle_validIdToken_shouldReturn200() throws Exception {
            GoogleLoginRequest request = new GoogleLoginRequest("valid.google.id.token");

            TokenResponse fakeToken = TokenResponse.builder()
                    .accessToken("access_token_abc")
                    .refreshToken("refresh_token_xyz")
                    .build();
            when(identityService.loginWithGoogle(any(GoogleLoginRequest.class))).thenReturn(fakeToken);

            mockMvc.perform(post("/api/auth/google")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS_0200"))
                    .andExpect(jsonPath("$.data.accessToken").value("access_token_abc"))
                    .andExpect(jsonPath("$.data.refreshToken").value("refresh_token_xyz"));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // POST /api/auth/refresh
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/refresh")
    class RefreshToken {

        @Test
        @DisplayName("refreshToken rỗng (blank string) → 400 VAL_3001")
        void refreshToken_refreshTokenBlank_shouldReturn400() throws Exception {
            RefreshTokenRequest request = new RefreshTokenRequest("");

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VAL_3001"))
                    .andExpect(jsonPath("$.message").value("refreshToken: Mã làm mới phiên đăng nhập không được để trống"));
        }

        @Test
        @DisplayName("refreshToken null (thiếu field trong JSON) → 400 VAL_3001")
        void refreshToken_refreshTokenNull_shouldReturn400() throws Exception {
            String body = objectMapper.writeValueAsString(Map.of());

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VAL_3001"))
                    .andExpect(jsonPath("$.message").value("refreshToken: Mã làm mới phiên đăng nhập không được để trống"));
        }

        @Test
        @DisplayName("refreshToken hợp lệ → 200 SUCCESS_0200")
        void refreshToken_validToken_shouldReturn200() throws Exception {
            RefreshTokenRequest request = new RefreshTokenRequest("valid_refresh_token");

            TokenResponse fakeToken = TokenResponse.builder()
                    .accessToken("new_access_token")
                    .refreshToken("new_refresh_token")
                    .build();
            when(identityService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(fakeToken);

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS_0200"))
                    .andExpect(jsonPath("$.data.accessToken").value("new_access_token"))
                    .andExpect(jsonPath("$.data.refreshToken").value("new_refresh_token"));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // POST /api/auth/logout
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/logout")
    class Logout {

        @Test
        @DisplayName("refreshToken rỗng (blank string) → 400 VAL_3001")
        void logout_refreshTokenBlank_shouldReturn400() throws Exception {
            RefreshTokenRequest request = new RefreshTokenRequest("");

            mockMvc.perform(post("/api/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VAL_3001"))
                    .andExpect(jsonPath("$.message").value("refreshToken: Mã làm mới phiên đăng nhập không được để trống"));
        }

        @Test
        @DisplayName("refreshToken null (thiếu field trong JSON) → 400 VAL_3001")
        void logout_refreshTokenNull_shouldReturn400() throws Exception {
            String body = objectMapper.writeValueAsString(Map.of());

            mockMvc.perform(post("/api/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VAL_3001"))
                    .andExpect(jsonPath("$.message").value("refreshToken: Mã làm mới phiên đăng nhập không được để trống"));
        }

        @Test
        @DisplayName("refreshToken hợp lệ → 200 SUCCESS_0200")
        void logout_validToken_shouldReturn200() throws Exception {
            RefreshTokenRequest request = new RefreshTokenRequest("valid_refresh_token");

            doNothing().when(identityService).logout("valid_refresh_token");

            mockMvc.perform(post("/api/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS_0200"))
                    .andExpect(jsonPath("$.data").value("Đăng xuất thành công"));
        }
    }
}
