package com.fptu.exe.skillswap.modules.mentor.port.dto;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MentorAdminFilterQuery extends BasePageRequest {
    private String keyword;
    private MentorStatus status;
    private Boolean isAvailable;
}
