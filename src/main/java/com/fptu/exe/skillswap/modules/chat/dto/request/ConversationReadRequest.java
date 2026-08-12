package com.fptu.exe.skillswap.modules.chat.dto.request;
import jakarta.validation.constraints.*;
public record ConversationReadRequest(@PositiveOrZero long lastReadSequence) {}
