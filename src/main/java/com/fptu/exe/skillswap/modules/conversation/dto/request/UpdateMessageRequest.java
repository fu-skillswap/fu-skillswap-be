package com.fptu.exe.skillswap.modules.conversation.dto.request;
import jakarta.validation.constraints.*;
public record UpdateMessageRequest(@NotBlank @Size(max=2000) String content, @NotNull @PositiveOrZero Integer expectedVersion) {}
