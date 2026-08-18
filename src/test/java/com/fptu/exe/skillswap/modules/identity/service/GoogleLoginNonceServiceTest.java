package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.infrastructure.config.CacheProperties;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoogleLoginNonceServiceTest {

    private final GoogleLoginNonceService service = new GoogleLoginNonceService(new CacheProperties());

    @Test
    void nonce_shouldBeRandomAndConsumableExactlyOnce() {
        var first = service.issue();
        var second = service.issue();

        assertFalse(first.nonce().equals(second.nonce()));
        assertDoesNotThrow(() -> service.consume(first.nonce()));
        assertThrows(BaseException.class, () -> service.consume(first.nonce()));
    }
}
