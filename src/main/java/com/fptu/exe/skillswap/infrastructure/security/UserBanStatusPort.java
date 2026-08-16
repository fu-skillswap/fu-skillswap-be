package com.fptu.exe.skillswap.infrastructure.security;

import java.util.UUID;

/**
 * Port ở infrastructure để kiểm tra user có bị cấm hay không.
 * Module identity triển khai để tránh infrastructure phụ thuộc trực tiếp vào modules.
 */
public interface UserBanStatusPort {

    /**
     * Trả về true nếu user đang BANNED.
     * User không tồn tại cũng được xem là bị chặn.
     */
    boolean isBanned(UUID userId);
}
