package com.fptu.exe.skillswap.modules.mentor.port;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminMentorListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminMentorDetailResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminMentorListItemResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminUserSummaryMentorProfileResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;

import java.util.UUID;

public interface MentorAdminPort {
    PageResponse<AdminMentorListItemResponse> getMentors(AdminMentorListRequest request);
    AdminMentorDetailResponse getMentorDetail(UUID mentorUserId);
    AdminUserSummaryMentorProfileResponse getMentorProfileSummary(UUID mentorUserId);
    long countMentorsWithPendingVerification();
}
