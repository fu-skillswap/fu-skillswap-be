package com.fptu.exe.skillswap.modules.forum.port;

import com.fptu.exe.skillswap.modules.forum.dto.request.ForumProhibitedPhraseActiveRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumProhibitedPhraseCreateRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumProhibitedPhraseUpdateRequest;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumProhibitedPhraseResponse;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;

import java.util.UUID;

public interface ForumProhibitedPhraseAdminPort {
    CursorPageResponse<ForumProhibitedPhraseResponse> list(Boolean isActive, String cursor, Integer limit);
    ForumProhibitedPhraseResponse get(UUID ruleId);
    ForumProhibitedPhraseResponse create(UUID adminUserId, ForumProhibitedPhraseCreateRequest request);
    ForumProhibitedPhraseResponse update(UUID adminUserId, UUID ruleId, ForumProhibitedPhraseUpdateRequest request);
    ForumProhibitedPhraseResponse setActive(UUID adminUserId, UUID ruleId, ForumProhibitedPhraseActiveRequest request);
}
