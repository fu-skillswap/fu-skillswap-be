package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.infrastructure.config.GoogleApiProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class GoogleTokenCryptoServiceTest {

    @Test
    void shouldEncryptAndDecryptRoundTrip() {
        GoogleApiProperties properties = new GoogleApiProperties();
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) (i + 1);
        }
        properties.setTokenEncryptionKey(Base64.getEncoder().encodeToString(key));
        properties.setTokenEncryptionKeyVersion(2);

        GoogleTokenCryptoService service = new GoogleTokenCryptoService(properties);
        String encrypted = service.encrypt("refresh-token-value");

        assertNotEquals("refresh-token-value", encrypted);
        assertEquals("refresh-token-value", service.decrypt(encrypted));
        assertEquals(2, service.currentKeyVersion());
    }
}
