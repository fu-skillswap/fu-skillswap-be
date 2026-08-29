package com.fptu.exe.skillswap.modules.course.dto.request;

import jakarta.validation.constraints.NotBlank;

/** The client confirms only the immutable upload target issued by the server. */
public record ConfirmCoursePdfUploadRequest(@NotBlank String objectKey) {
}
