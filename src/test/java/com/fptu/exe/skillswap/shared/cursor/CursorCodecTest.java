package com.fptu.exe.skillswap.shared.cursor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CursorCodecTest {

    private CursorCodec cursorCodec;

    @BeforeEach
    void setUp() {
        CursorCryptoProperties properties = new CursorCryptoProperties();
        properties.setVersion("v1");
        properties.setAesKey(Base64.getEncoder().encodeToString("1234567890abcdef1234567890abcdef".getBytes()));
        properties.setHmacKey(Base64.getEncoder().encodeToString("1234567890abcdef1234567890abcdef".getBytes()));
        properties.validate();
        cursorCodec = new CursorCodec(properties, new ObjectMapper());
    }

    @Test
    void shouldEncodeAndDecodeCursorPayload() {
        CursorTokenPayload payload = CursorTokenPayload.builder()
                .sortKey("2026-07-08T10:15:00")
                .secondaryKey("019f1234")
                .direction("NEXT")
                .filterHash("abc123")
                .issuedAt(Instant.parse("2026-07-08T03:15:00Z"))
                .build();

        String token = cursorCodec.encode(payload);
        CursorTokenPayload decoded = cursorCodec.decode(token);

        assertNotNull(token);
        assertEquals(payload, decoded);
    }

    @Test
    void shouldRejectCursorWithInvalidSignature() {
        CursorTokenPayload payload = CursorTokenPayload.builder()
                .sortKey("A")
                .secondaryKey("B")
                .direction("NEXT")
                .filterHash("hash")
                .build();

        String token = cursorCodec.encode(payload);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThrows(BaseException.class, () -> cursorCodec.decode(tampered));
    }

    @Test
    void shouldRejectMalformedCursor() {
        assertThrows(BaseException.class, () -> cursorCodec.decode("not.a.valid.token"));
    }

    @Test
    void shouldRejectCursorWithWrongVersion() {
        CursorTokenPayload payload = CursorTokenPayload.builder()
                .sortKey("A")
                .secondaryKey("B")
                .direction("NEXT")
                .filterHash("hash")
                .build();

        String token = cursorCodec.encode(payload);
        String[] parts = token.split("\\.");
        parts[0] = Base64.getUrlEncoder().withoutPadding().encodeToString("v2".getBytes());
        String wrongVersion = String.join(".", parts);

        assertThrows(BaseException.class, () -> cursorCodec.decode(wrongVersion));
    }
}
