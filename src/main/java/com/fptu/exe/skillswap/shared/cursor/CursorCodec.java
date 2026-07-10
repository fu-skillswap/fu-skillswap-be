package com.fptu.exe.skillswap.shared.cursor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class CursorCodec {

    private static final String AES_TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TOKEN_PART_SEPARATOR = ".";
    private static final int IV_LENGTH_BYTES = 16;

    private final CursorCryptoProperties properties;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public String encode(CursorTokenPayload payload) {
        if (payload == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cursor payload không được để trống");
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            byte[] plaintext = objectMapper.copy().findAndRegisterModules().writeValueAsBytes(payload);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(properties.aesKeyBytes(), "AES"), new IvParameterSpec(iv));
            byte[] ciphertext = cipher.doFinal(plaintext);

            String versionPart = base64UrlEncode(properties.getVersion().getBytes(StandardCharsets.UTF_8));
            String ivPart = base64UrlEncode(iv);
            String ciphertextPart = base64UrlEncode(ciphertext);
            String signingInput = String.join(TOKEN_PART_SEPARATOR, versionPart, ivPart, ciphertextPart);
            String macPart = base64UrlEncode(sign(signingInput.getBytes(StandardCharsets.UTF_8)));
            return String.join(TOKEN_PART_SEPARATOR, signingInput, macPart);
        } catch (BaseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Không thể mã hóa cursor", ex);
        }
    }

    public CursorTokenPayload decode(String token) {
        if (token == null || token.isBlank()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cursor không được để trống");
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 4) {
                throw invalidCursor("Cursor không đúng định dạng");
            }
            String version = new String(base64UrlDecode(parts[0]), StandardCharsets.UTF_8);
            if (!properties.getVersion().equals(version)) {
                throw invalidCursor("Cursor version không hợp lệ");
            }

            String signingInput = String.join(TOKEN_PART_SEPARATOR, parts[0], parts[1], parts[2]);
            byte[] expectedMac = sign(signingInput.getBytes(StandardCharsets.UTF_8));
            byte[] actualMac = base64UrlDecode(parts[3]);
            if (!MessageDigest.isEqual(expectedMac, actualMac)) {
                throw invalidCursor("Cursor signature không hợp lệ");
            }

            byte[] iv = base64UrlDecode(parts[1]);
            if (iv.length != IV_LENGTH_BYTES) {
                throw invalidCursor("Cursor IV không hợp lệ");
            }
            byte[] ciphertext = base64UrlDecode(parts[2]);

            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(properties.aesKeyBytes(), "AES"), new IvParameterSpec(iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return objectMapper.copy().findAndRegisterModules().readValue(plaintext, CursorTokenPayload.class);
        } catch (BaseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalidCursor("Không thể giải mã cursor");
        }
    }

    private byte[] sign(byte[] data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(properties.hmacKeyBytes(), HMAC_ALGORITHM));
        return mac.doFinal(data);
    }

    private String base64UrlEncode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private byte[] base64UrlDecode(String value) {
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            throw invalidCursor("Cursor chứa dữ liệu base64url không hợp lệ");
        }
    }

    private BaseException invalidCursor(String message) {
        return new BaseException(ErrorCode.BAD_REQUEST, message);
    }
}
