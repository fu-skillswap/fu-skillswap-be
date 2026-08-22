package com.fptu.exe.skillswap.modules.mentor.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceResourceType;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceResourceVisibility;
import jakarta.validation.constraints.*;
import java.util.UUID;

@Schema(description = "Yêu cầu gắn tài liệu vào gói dịch vụ sau khi upload thành công")
public record MentorServiceResourceCreateRequest(
        @Schema(description = "Mã upload intent do API createUploadUrl trả về", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull UUID uploadIntentId,

        @Schema(description = "Tiêu đề tài liệu", example = "Slide Bài giảng Microservices", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max=255) String title,

        @Schema(description = "Mô tả tài liệu", example = "Tài liệu tóm tắt kiến trúc hệ thống")
        @Size(max=4000) String description,

        @Schema(
                description = "Loại định dạng tài liệu (Bắt buộc):<br/>"
                        + "• `PDF`: File tài liệu PDF (.pdf, tối đa 20MB)<br/>"
                        + "• `DOCX`: File Word (.docx, tối đa 20MB)<br/>"
                        + "• `PPTX`: File PowerPoint (.pptx, tối đa 20MB)<br/>"
                        + "• `TEXT`: File văn bản thuần (.txt, tối đa 20MB)<br/>"
                        + "• `MARKDOWN`: File Markdown (.md, tối đa 20MB)<br/>"
                        + "• `PNG`: Ảnh PNG (.png, tối đa 10MB)<br/>"
                        + "• `JPEG`: Ảnh JPG/JPEG (.jpg, .jpeg, tối đa 10MB)",
                example = "PDF",
                allowableValues = {"PDF", "DOCX", "PPTX", "TEXT", "MARKDOWN", "PNG", "JPEG"},
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull MentorServiceResourceType resourceType,

        @Schema(
                description = "Phân quyền xem tài liệu (Bắt buộc):<br/>"
                        + "• `AUTHENTICATED`: Bất kỳ người dùng nào đã đăng nhập đều có thể xem/tải<br/>"
                        + "• `BOOKED_MEMBERS`: Chỉ mentee đã thanh toán đặt lịch dịch vụ này mới được xem/tải",
                example = "BOOKED_MEMBERS",
                allowableValues = {"AUTHENTICATED", "BOOKED_MEMBERS"},
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull MentorServiceResourceVisibility visibility
) {}

