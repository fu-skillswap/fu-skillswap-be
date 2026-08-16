package com.fptu.exe.skillswap.shared.util;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Tiện ích tạo UUID v7 có thứ tự theo thời gian.
 * Tuân theo RFC 9562.
 */
public class UuidUtil {
    private static final SecureRandom random = new SecureRandom();

    /**
     * Tạo UUID v7 có thứ tự theo thời gian.
     * Gồm 48 bit timestamp, 4 bit version (7), 2 bit variant (2) và 74 bit ngẫu nhiên.
     *
     * @return UUID v7 có thứ tự theo thời gian.
     */
    public static UUID generateUuidV7() {
        long timestamp = System.currentTimeMillis();

        // randA: 12 bit ngẫu nhiên.
        long randA = random.nextInt(0x1000); // Từ 0 đến 4095.

        // randB: 62 bit ngẫu nhiên.
        long randB = random.nextLong();

        // Phần bit có ý nghĩa cao (msb).
        // 48 bit timestamp | 4 bit version (7) | 12 bit randA.
        long msb = (timestamp << 16) | (7L << 12) | randA;

        // Phần bit có ý nghĩa thấp (lsb).
        // 2 bit variant (2, binary 10) | 62 bit randB.
        long lsb = (2L << 62) | (randB & 0x3FFFFFFFFFFFFFFFL);

        return new UUID(msb, lsb);
    }
}
