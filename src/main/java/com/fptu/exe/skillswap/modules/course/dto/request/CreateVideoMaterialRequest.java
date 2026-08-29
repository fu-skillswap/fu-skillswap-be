package com.fptu.exe.skillswap.modules.course.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateVideoMaterialRequest {
    @NotBlank(message = "Title is required")
    private String title;
    
    @NotNull(message = "sortOrder is required")
    private Integer sortOrder;

    private Boolean previewable;

    private Boolean published;
}
