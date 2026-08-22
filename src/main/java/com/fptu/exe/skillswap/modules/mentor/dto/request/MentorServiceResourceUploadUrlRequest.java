package com.fptu.exe.skillswap.modules.mentor.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceResourceType;
import jakarta.validation.constraints.*;

@Schema(description = "Yêu cầu tạo URL tải lên tài nguyên dịch vụ")
public record MentorServiceResourceUploadUrlRequest(
        @Schema(description = "Tên file gốc đính kèm đuôi mở rộng", example = "lecture_notes.pdf", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max=255) String filename,

        @Schema(
                description = "Loại tài nguyên dự kiến tải lên (Bắt buộc):<br/>"
                        + "• `PDF`: Tài liệu PDF (.pdf)<br/>"
                        + "• `DOCX`: Microsoft Word (.docx)<br/>"
                        + "• `PPTX`: Microsoft PowerPoint (.pptx)<br/>"
                        + "• `TEXT`: Văn bản thuần (.txt)<br/>"
                        + "• `MARKDOWN`: Markdown (.md)<br/>"
                        + "• `PNG`: Ảnh PNG (.png)<br/>"
                        + "• `JPEG`: Ảnh JPEG (.jpg, .jpeg)",
                example = "PDF",
                allowableValues = {"PDF", "DOCX", "PPTX", "TEXT", "MARKDOWN", "PNG", "JPEG"},
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull MentorServiceResourceType resourceType
) {}

