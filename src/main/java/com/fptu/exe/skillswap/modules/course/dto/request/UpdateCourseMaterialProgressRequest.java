package com.fptu.exe.skillswap.modules.course.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cập nhật số giây video mà người học đã xem. Backend gắn tiến độ với tài khoản đăng nhập và tài liệu trong URL.")
public record UpdateCourseMaterialProgressRequest(@NotNull @Min(0) Integer watchedSeconds) {
}
