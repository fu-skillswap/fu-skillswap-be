package com.fptu.exe.skillswap.modules.course.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Provider-neutral webhook data consumed by the Course workflow. */
@Schema(description = "Internal/System - không dùng cho FE. Dữ liệu callback video sau khi adapter chuyển từ provider về dạng nội bộ.")
public record CourseVideoWebhook(String videoId, String libraryId, int status) {
}
