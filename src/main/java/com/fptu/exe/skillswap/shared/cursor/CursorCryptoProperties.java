package com.fptu.exe.skillswap.shared.cursor;

import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Base64;

@Configuration
@ConfigurationProperties(prefix = "application.cursor.crypto")
@Getter
@Setter
public class CursorCryptoProperties {

    private String version = "v1";
    private String aesKey;
    private String hmacKey;

    @PostConstruct
    void validate() {
        if (!StringUtils.hasText(version)) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Thiếu cấu hình application.cursor.crypto.version");
        }
        byte[] aesKeyBytes = decodeBase64(aesKey, "application.cursor.crypto.aes-key");
        if (aesKeyBytes.length != 16 && aesKeyBytes.length != 24 && aesKeyBytes.length != 32) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "application.cursor.crypto.aes-key phải decode ra 16, 24 hoặc 32 bytes");
        }
        byte[] hmacKeyBytes = decodeBase64(hmacKey, "application.cursor.crypto.hmac-key");
        if (hmacKeyBytes.length < 32) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "application.cursor.crypto.hmac-key phải decode ra ít nhất 32 bytes");
        }
    }

    public byte[] aesKeyBytes() {
        return decodeBase64(aesKey, "application.cursor.crypto.aes-key");
    }

    public byte[] hmacKeyBytes() {
        return decodeBase64(hmacKey, "application.cursor.crypto.hmac-key");
    }

    private byte[] decodeBase64(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Thiếu cấu hình " + propertyName);
        }
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, propertyName + " phải là base64 hợp lệ", ex);
        }
    }
}
