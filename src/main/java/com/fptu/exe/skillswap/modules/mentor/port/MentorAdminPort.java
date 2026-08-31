package com.fptu.exe.skillswap.modules.mentor.port;

import com.fptu.exe.skillswap.modules.mentor.dto.request.AdminMentorListRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.AdminMentorDetailResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.AdminMentorListItemResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.AdminUserSummaryMentorProfileResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;

import java.util.UUID;

public interface MentorAdminPort {
    PageResponse<AdminMentorListItemResponse> getMentors(AdminMentorListRequest request);
    AdminMentorDetailResponse getMentorDetail(UUID mentorUserId);
    AdminUserSummaryMentorProfileResponse getMentorProfileSummary(UUID mentorUserId);
    long countMentorsWithPendingVerification();
    boolean existsById(UUID mentorId);
}
