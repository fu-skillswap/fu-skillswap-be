package com.fptu.exe.skillswap.modules.forum.port;

import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;

import java.util.UUID;

public interface ForumProhibitedPhraseAdminPort {
    CursorPageResponse<ForumProhibitedPhraseView> list(Boolean isActive, String cursor, Integer limit);
    ForumProhibitedPhraseView get(UUID ruleId);
    ForumProhibitedPhraseView create(UUID adminUserId, CreateForumProhibitedPhraseCommand command);
    ForumProhibitedPhraseView update(UUID adminUserId, UUID ruleId, UpdateForumProhibitedPhraseCommand command);
    ForumProhibitedPhraseView setActive(UUID adminUserId, UUID ruleId, SetForumProhibitedPhraseActiveCommand command);
}
