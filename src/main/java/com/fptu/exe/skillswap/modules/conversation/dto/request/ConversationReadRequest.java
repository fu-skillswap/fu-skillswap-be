package com.fptu.exe.skillswap.modules.conversation.dto.request;
import jakarta.validation.constraints.*;
public record ConversationReadRequest(@PositiveOrZero long lastReadSequence) {}
