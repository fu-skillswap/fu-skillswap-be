package com.fptu.exe.skillswap.modules.mentor.port;

import com.fptu.exe.skillswap.modules.mentor.port.dto.MentorAdminDetailDto;
import com.fptu.exe.skillswap.modules.mentor.port.dto.MentorAdminFilterQuery;
import com.fptu.exe.skillswap.modules.mentor.port.dto.MentorAdminListItemDto;
import com.fptu.exe.skillswap.modules.mentor.port.dto.MentorSummaryProfileDto;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;

import java.util.UUID;

public interface MentorAdminPort {
    PageResponse<MentorAdminListItemDto> getMentors(MentorAdminFilterQuery query);
    MentorAdminDetailDto getMentorDetail(UUID mentorUserId);
    MentorSummaryProfileDto getMentorProfileSummary(UUID mentorUserId);
    long countMentorsWithPendingVerification();
}
