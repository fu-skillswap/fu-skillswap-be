package com.fptu.exe.skillswap.modules.chat.dto.response;
import java.util.UUID;
public record ConversationReadResponse(UUID conversationId,long myLastReadSequence,long otherLastReadSequence,long unreadCount) {}
