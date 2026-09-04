package com.fptu.exe.skillswap.modules.chat.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Quyền gửi tin nhắn hiện tại. OPEN: FE có thể bật ô nhập và gửi nếu canSendMessages=true; READ_ONLY: vẫn có thể đọc lịch sử nhưng không gửi, FE hiển thị readOnlyReason và không retry gửi tự động.")
public enum ChatMessagingAccess { OPEN, READ_ONLY }
