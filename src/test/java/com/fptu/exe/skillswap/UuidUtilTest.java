package com.fptu.exe.skillswap;

import com.fptu.exe.skillswap.shared.util.UuidUtil;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

public class UuidUtilTest {

    @Test
    void testUuidV7Generation() {
        UUID uuid = UuidUtil.generateUuidV7();
        assertNotNull(uuid);
        
        // Check version (must be 7)
        assertEquals(7, uuid.version());
        
        // Check variant (must be 2 - RFC 4122/9562 layout)
        assertEquals(2, uuid.variant());
    }

    @Test
    void testUuidV7Monotonicity() throws InterruptedException {
        UUID first = UuidUtil.generateUuidV7();
        Thread.sleep(5);
        UUID second = UuidUtil.generateUuidV7();

        // Lexicographical order: later generated UUID should be greater than previous one
        assertTrue(second.compareTo(first) > 0, "Second UUID (" + second + ") should be greater than First UUID (" + first + ")");
    }
}
