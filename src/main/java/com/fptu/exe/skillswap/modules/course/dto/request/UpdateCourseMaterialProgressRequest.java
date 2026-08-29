package com.fptu.exe.skillswap.modules.course.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateCourseMaterialProgressRequest(@NotNull @Min(0) Integer watchedSeconds) {
}
