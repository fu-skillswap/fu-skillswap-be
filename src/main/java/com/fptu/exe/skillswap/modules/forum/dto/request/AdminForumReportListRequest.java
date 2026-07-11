package com.fptu.exe.skillswap.modules.forum.dto.request;

import com.fptu.exe.skillswap.modules.forum.domain.ForumReportStatus;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Bộ lọc danh sách forum reports cho admin")
public record AdminForumReportListRequest(
        Integer page,
        Integer size,
        String keyword,
        ForumReportStatus status,
        ForumReportTargetType targetType
) {
}
