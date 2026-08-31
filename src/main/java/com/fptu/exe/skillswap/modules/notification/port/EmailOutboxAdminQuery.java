package com.fptu.exe.skillswap.modules.notification.port;

import java.time.LocalDateTime;

public record EmailOutboxAdminQuery(String status, String templateCode, String toEmail,
                                    LocalDateTime from, LocalDateTime to, int page, int size,
                                    String sortBy, String direction) { }
