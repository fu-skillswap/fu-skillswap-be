package com.fptu.exe.skillswap.modules.mentor.dto.request;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminMentorListRequest extends BasePageRequest {

    @Schema(description = "Tìm theo email, tên hiển thị hoặc headline của mentor", example = "backend")
    private String keyword;

    @Schema(description = "Lọc theo trạng thái mentor", example = "ACTIVE")
    private MentorStatus status;

    @Schema(description = "Lọc theo trạng thái đang nhận lịch")
    private Boolean isAvailable;

    public AdminMentorListRequest() {
        setSortBy("updatedAt");
        setDirection("DESC");
        setSize(20);
    }
}
