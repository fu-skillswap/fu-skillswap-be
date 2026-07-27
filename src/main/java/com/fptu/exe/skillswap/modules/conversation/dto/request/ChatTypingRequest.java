package com.fptu.exe.skillswap.modules.conversation.dto.request;
import java.util.UUID;
public record ChatTypingRequest(UUID conversationId, boolean typing) {}
