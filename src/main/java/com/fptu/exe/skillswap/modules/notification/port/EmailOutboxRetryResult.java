package com.fptu.exe.skillswap.modules.notification.port;

public record EmailOutboxRetryResult(EmailOutboxAdminDetail email,
                                     String previousStatus, Integer previousRetryCount, String previousLastError) { }
