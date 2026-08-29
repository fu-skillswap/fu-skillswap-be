package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminMentorListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminMentorDetailResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminMentorListItemResponse;
import com.fptu.exe.skillswap.modules.mentor.port.MentorAdminPort;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminMentorModerationService {

    private final MentorAdminPort mentorAdminPort;

    @Transactional(readOnly = true)
    public PageResponse<AdminMentorListItemResponse> getMentors(AdminMentorListRequest request) {
        return mentorAdminPort.getMentors(request);
    }

    @Transactional(readOnly = true)
    public AdminMentorDetailResponse getMentorDetail(UUID mentorUserId) {
        return mentorAdminPort.getMentorDetail(mentorUserId);
    }
}
