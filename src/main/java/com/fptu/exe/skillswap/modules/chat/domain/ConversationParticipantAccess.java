package com.fptu.exe.skillswap.modules.chat.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Access is derived from booking/group lifecycle and persisted only as the current chat gate. */
@Schema(description = "Quyền của participant trong conversation. ACTIVE: được dùng chat theo quyền hiện tại; READ_ONLY: được đọc nhưng không gửi; REVOKED: không còn quyền thao tác mới. FE dùng canSendMessages/canUploadAttachments và readOnlyReason để điều khiển UI.")
public enum ConversationParticipantAccess {
    ACTIVE,
    READ_ONLY,
    REVOKED
}
