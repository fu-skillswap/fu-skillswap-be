package com.fptu.exe.skillswap.modules.chat.dto.request;
import java.util.UUID;
public record ChatTypingRequest(UUID conversationId, boolean typing) {}
