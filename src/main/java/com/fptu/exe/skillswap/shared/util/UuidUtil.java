package com.fptu.exe.skillswap.shared.util;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Utility class to generate time-ordered UUID v7.
 * RFC 9562 compliant.
 */
public class UuidUtil {
    private static final SecureRandom random = new SecureRandom();

    /**
     * Generates a time-ordered UUID v7.
     * Contains 48 bits of timestamp, 4 bits of version (7), 2 bits of variant (2),
     * and 74 random bits.
     * 
     * @return a time-ordered {@link UUID} v7
     */
    public static UUID generateUuidV7() {
        long timestamp = System.currentTimeMillis();

        // randA: 12 bits of randomness
        long randA = random.nextInt(0x1000); // 0 to 4095

        // randB: 62 bits of randomness
        long randB = random.nextLong();

        // Most Significant Bits (msb):
        // 48 bits timestamp | 4 bits version (7) | 12 bits randA
        long msb = (timestamp << 16) | (7L << 12) | randA;

        // Least Significant Bits (lsb):
        // 2 bits variant (2, binary 10) | 62 bits randB
        long lsb = (2L << 62) | (randB & 0x3FFFFFFFFFFFFFFFL);

        return new UUID(msb, lsb);
    }
}
