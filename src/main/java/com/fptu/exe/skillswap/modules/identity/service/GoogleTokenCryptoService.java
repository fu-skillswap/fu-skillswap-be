package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.infrastructure.config.JwtProperties;
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
import java.security.SecureRandom;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class GoogleTokenCryptoService {

    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public String encrypt(String plaintext) {
        if (!StringUtils.hasText(plaintext)) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, resolveKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(ciphertext);
        } catch (Exception ex) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Không thể mã hóa token Google Calendar");
        }
    }

    public String decrypt(String encrypted) {
        if (!StringUtils.hasText(encrypted)) {
            return null;
        }
        try {
            String[] parts = encrypted.split("\\.", 2);
            if (parts.length != 2) {
                throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Token Google Calendar đã lưu không hợp lệ");
            }
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[1]);

            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, resolveKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (BaseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Không thể giải mã token Google Calendar");
        }
    }

    public int currentKeyVersion() {
        return jwtProperties.getGoogle().getTokenEncryptionKeyVersion() == null
                ? 1
                : jwtProperties.getGoogle().getTokenEncryptionKeyVersion();
    }

    private SecretKey resolveKey() {
        String configuredKey = jwtProperties.getGoogle().getTokenEncryptionKey();
        if (!StringUtils.hasText(configuredKey)) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Thiếu cấu hình GOOGLE_TOKEN_ENCRYPTION_KEY");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(configuredKey);
            if (decoded.length != 32) {
                throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "GOOGLE_TOKEN_ENCRYPTION_KEY phải là base64 của khóa 32 bytes");
            }
            return new SecretKeySpec(decoded, "AES");
        } catch (IllegalArgumentException ex) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "GOOGLE_TOKEN_ENCRYPTION_KEY phải là base64 hợp lệ");
        }
    }
}
