package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.infrastructure.config.JwtProperties;
import com.fptu.exe.skillswap.modules.identity.dto.response.TokenResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class RefreshTokenReplayCryptoService {

    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public String encrypt(TokenResponse tokenResponse) {
        if (tokenResponse == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, resolveKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(serialize(tokenResponse).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(ciphertext);
        } catch (Exception ex) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Không thể mã hóa dữ liệu refresh replay");
        }
    }

    public TokenResponse decrypt(String encrypted) {
        if (!StringUtils.hasText(encrypted)) {
            return null;
        }
        try {
            String[] parts = encrypted.split("\\.", 2);
            if (parts.length != 2) {
                throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Dữ liệu refresh replay không hợp lệ");
            }
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[1]);

            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, resolveKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return deserialize(new String(plaintext, StandardCharsets.UTF_8));
        } catch (BaseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Không thể giải mã dữ liệu refresh replay");
        }
    }

    private String serialize(TokenResponse tokenResponse) {
        String tokenType = StringUtils.hasText(tokenResponse.getTokenType()) ? tokenResponse.getTokenType() : "Bearer";
        String accessToken = tokenResponse.getAccessToken() == null ? "" : tokenResponse.getAccessToken();
        String refreshToken = tokenResponse.getRefreshToken() == null ? "" : tokenResponse.getRefreshToken();
        return tokenType + "\n" + accessToken + "\n" + refreshToken;
    }

    private TokenResponse deserialize(String plaintext) {
        String[] parts = plaintext.split("\\n", 3);
        if (parts.length != 3) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Dữ liệu refresh replay không hợp lệ");
        }
        return TokenResponse.builder()
                .tokenType(StringUtils.hasText(parts[0]) ? parts[0] : "Bearer")
                .accessToken(parts[1])
                .refreshToken(parts[2])
                .build();
    }

    private SecretKey resolveKey() {
        String secret = jwtProperties.getJwt().getSecretKey();
        if (!StringUtils.hasText(secret)) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Thiếu cấu hình JWT_SECRET_KEY cho refresh replay");
        }
        try {
            byte[] rawSecret;
            try {
                rawSecret = Base64.getDecoder().decode(secret);
            } catch (IllegalArgumentException ex) {
                rawSecret = secret.getBytes(StandardCharsets.UTF_8);
            }
            byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(rawSecret);
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception ex) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Không thể khởi tạo khóa mã hóa refresh replay");
        }
    }
}
