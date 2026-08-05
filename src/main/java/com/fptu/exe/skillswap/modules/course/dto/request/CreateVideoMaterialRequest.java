package com.fptu.exe.skillswap.modules.course.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateVideoMaterialRequest {
    @NotBlank(message = "Title is required")
    private String title;
    
    private UUID courseSessionId;
}
