package com.fptu.exe.skillswap.infrastructure.storage;

import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DisabledStorageGatewayTest {

    private final DisabledStorageGateway gateway = new DisabledStorageGateway();

    @Test
    void failsClosedWithoutReturningAnyStorageCredential() {
        BaseException exception = assertThrows(BaseException.class,
                () -> gateway.generatePrivateDownloadUrl("chat-attachments/private.pdf", java.time.Duration.ofMinutes(10), "attachment"));

        assertEquals(ErrorCode.STORAGE_ERROR, exception.getErrorCode());
    }
}
