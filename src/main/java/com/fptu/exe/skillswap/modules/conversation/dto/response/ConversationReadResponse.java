package com.fptu.exe.skillswap.modules.conversation.dto.response;
import java.util.UUID;
public record ConversationReadResponse(UUID conversationId,long myLastReadSequence,long otherLastReadSequence,long unreadCount) {}
