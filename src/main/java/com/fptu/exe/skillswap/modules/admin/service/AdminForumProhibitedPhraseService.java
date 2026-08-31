package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.forum.port.CreateForumProhibitedPhraseCommand;
import com.fptu.exe.skillswap.modules.forum.port.ForumProhibitedPhraseView;
import com.fptu.exe.skillswap.modules.forum.port.ForumProhibitedPhraseAdminPort;
import com.fptu.exe.skillswap.modules.forum.port.SetForumProhibitedPhraseActiveCommand;
import com.fptu.exe.skillswap.modules.forum.port.UpdateForumProhibitedPhraseCommand;
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
    public CursorPageResponse<ForumProhibitedPhraseView> list(Boolean isActive, String cursor, Integer limit) {
        return forumProhibitedPhraseAdminPort.list(isActive, cursor, limit);
    }

    @Transactional(readOnly = true)
    public ForumProhibitedPhraseView get(UUID ruleId) {
        return forumProhibitedPhraseAdminPort.get(ruleId);
    }

    @Transactional
    public ForumProhibitedPhraseView create(UUID adminUserId, CreateForumProhibitedPhraseCommand command) {
        ForumProhibitedPhraseView response = forumProhibitedPhraseAdminPort.create(adminUserId, command);
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
    public ForumProhibitedPhraseView update(UUID adminUserId, UUID ruleId, UpdateForumProhibitedPhraseCommand command) {
        ForumProhibitedPhraseView previous = forumProhibitedPhraseAdminPort.get(ruleId);
        ForumProhibitedPhraseView response = forumProhibitedPhraseAdminPort.update(adminUserId, ruleId, command);
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
    public ForumProhibitedPhraseView changeActive(UUID adminUserId, UUID ruleId, SetForumProhibitedPhraseActiveCommand command) {
        ForumProhibitedPhraseView previous = forumProhibitedPhraseAdminPort.get(ruleId);
        ForumProhibitedPhraseView response = forumProhibitedPhraseAdminPort.setActive(adminUserId, ruleId, command);
        adminAuditWriterService.writeOperatorEvent(
                adminUserId,
                "FORUM_PROHIBITED_PHRASE",
                ruleId,
                command.isActive() ? "ACTIVATE_FORUM_PROHIBITED_PHRASE" : "DEACTIVATE_FORUM_PROHIBITED_PHRASE",
                auditValue(previous),
                auditValue(response)
        );
        return response;
    }

    private Map<String, Object> auditValue(ForumProhibitedPhraseView rule) {
        return Map.of(
                "phrase", rule.phrase() == null ? "" : rule.phrase(),
                "isActive", rule.isActive(),
                "version", rule.version() == null ? 0 : rule.version()
        );
    }
}
