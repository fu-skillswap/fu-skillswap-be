package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.forum.dto.request.ForumProhibitedPhraseActiveRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumProhibitedPhraseCreateRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumProhibitedPhraseUpdateRequest;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumProhibitedPhraseResponse;
import com.fptu.exe.skillswap.modules.forum.port.ForumProhibitedPhraseAdminPort;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminForumProhibitedPhraseService {

    private final ForumProhibitedPhraseAdminPort forumProhibitedPhraseAdminPort;
    private final AdminAuditWriterService adminAuditWriterService;

    @Transactional(readOnly = true)
    public CursorPageResponse<ForumProhibitedPhraseResponse> list(Boolean isActive, String cursor, Integer limit) {
        return forumProhibitedPhraseAdminPort.list(isActive, cursor, limit);
    }

    @Transactional(readOnly = true)
    public ForumProhibitedPhraseResponse get(UUID ruleId) {
        return forumProhibitedPhraseAdminPort.get(ruleId);
    }

    @Transactional
    public ForumProhibitedPhraseResponse create(UUID adminUserId, ForumProhibitedPhraseCreateRequest request) {
        ForumProhibitedPhraseResponse response = forumProhibitedPhraseAdminPort.create(adminUserId, request);
        adminAuditWriterService.writeOperatorEvent(
                adminUserId,
                "FORUM_PROHIBITED_PHRASE",
                response.ruleId(),
                "CREATE_FORUM_PROHIBITED_PHRASE",
                null,
                auditValue(response)
        );
        return response;
    }

    @Transactional
    public ForumProhibitedPhraseResponse update(UUID adminUserId, UUID ruleId, ForumProhibitedPhraseUpdateRequest request) {
        ForumProhibitedPhraseResponse previous = forumProhibitedPhraseAdminPort.get(ruleId);
        ForumProhibitedPhraseResponse response = forumProhibitedPhraseAdminPort.update(adminUserId, ruleId, request);
        adminAuditWriterService.writeOperatorEvent(
                adminUserId,
                "FORUM_PROHIBITED_PHRASE",
                ruleId,
                "UPDATE_FORUM_PROHIBITED_PHRASE",
                auditValue(previous),
                auditValue(response)
        );
        return response;
    }

    @Transactional
    public ForumProhibitedPhraseResponse changeActive(UUID adminUserId, UUID ruleId, ForumProhibitedPhraseActiveRequest request) {
        ForumProhibitedPhraseResponse previous = forumProhibitedPhraseAdminPort.get(ruleId);
        ForumProhibitedPhraseResponse response = forumProhibitedPhraseAdminPort.setActive(adminUserId, ruleId, request);
        adminAuditWriterService.writeOperatorEvent(
                adminUserId,
                "FORUM_PROHIBITED_PHRASE",
                ruleId,
                request.isActive() ? "ACTIVATE_FORUM_PROHIBITED_PHRASE" : "DEACTIVATE_FORUM_PROHIBITED_PHRASE",
                auditValue(previous),
                auditValue(response)
        );
        return response;
    }

    private Map<String, Object> auditValue(ForumProhibitedPhraseResponse rule) {
        return Map.of(
                "phrase", rule.phrase() == null ? "" : rule.phrase(),
                "isActive", rule.isActive(),
                "version", rule.version() == null ? 0 : rule.version()
        );
    }
}
