package com.fptu.exe.skillswap.modules.course.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ReorderCurriculumRequest(
        @NotEmpty List<UUID> orderedIds,
        @NotNull Long expectedContainerVersion
) {
}
