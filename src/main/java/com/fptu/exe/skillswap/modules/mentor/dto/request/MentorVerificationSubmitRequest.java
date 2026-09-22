package com.fptu.exe.skillswap.modules.mentor.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Yêu cầu nộp hồ sơ xét duyệt Mentor kèm xác nhận điều khoản và minh chứng tùy chọn")
public record MentorVerificationSubmitRequest(
        @Schema(description = "Ghi chú nộp hồ sơ gửi tới admin", example = "Em đã bổ sung đầy đủ minh chứng bằng cấp và thẻ sinh viên.")
        @Size(max = 2000, message = "Ghi chú nộp hồ sơ không được vượt quá 2000 ký tự")
        String submitNote,

        @Schema(description = "Đã đọc và đồng ý với điều khoản nền tảng", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Vui lòng xác nhận đã đọc và đồng ý với điều khoản")
        Boolean termsAccepted,

        @Schema(description = "Danh sách tài liệu minh chứng đính kèm khi submit trực tiếp (tùy chọn)")
        List<@Valid MentorVerificationDocumentUploadRequest> documents
) {

    public MentorVerificationSubmitRequest(String submitNote, Boolean termsAccepted) {
        this(submitNote, termsAccepted, null);
    }
}
