package com.fptu.exe.skillswap.modules.course.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateVideoMaterialRequest {
    @NotBlank(message = "Title is required")
    private String title;
    
    @NotNull(message = "lectureId is required")
    private UUID lectureId;
}
