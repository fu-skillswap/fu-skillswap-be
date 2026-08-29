package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.forum.domain.ForumProhibitedPhrase;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumProhibitedPhraseCreateRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumProhibitedPhraseUpdateRequest;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumProhibitedPhraseResponse;
import com.fptu.exe.skillswap.modules.forum.port.ForumProhibitedPhraseAdminPort;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.UuidUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminForumProhibitedPhraseServiceTest {

    @Mock
    private ForumProhibitedPhraseAdminPort forumProhibitedPhraseAdminPort;
    @Mock
    private AdminAuditWriterService adminAuditWriterService;

    private AdminForumProhibitedPhraseService service;
    private User admin;

    @BeforeEach
    void setUp() {
        service = new AdminForumProhibitedPhraseService(
                forumProhibitedPhraseAdminPort,
                adminAuditWriterService
        );
        admin = User.builder().id(UuidUtil.generateUuidV7()).fullName("Admin").build();
    }

    @Test
    void create_delegatesToPortAndWritesAudit() {
        UUID ruleId = UuidUtil.generateUuidV7();
        ForumProhibitedPhraseResponse mockResponse = new ForumProhibitedPhraseResponse(
                ruleId, "Cụm Từ Cấm", true, 0, admin.getId(), LocalDateTime.now(), LocalDateTime.now()
        );
        when(forumProhibitedPhraseAdminPort.create(eq(admin.getId()), any(ForumProhibitedPhraseCreateRequest.class)))
                .thenReturn(mockResponse);

        var response = service.create(admin.getId(), new ForumProhibitedPhraseCreateRequest("Cụm Từ Cấm"));

        assertEquals("Cụm Từ Cấm", response.phrase());
        assertEquals(true, response.isActive());
        verify(forumProhibitedPhraseAdminPort).create(eq(admin.getId()), any(ForumProhibitedPhraseCreateRequest.class));
        verify(adminAuditWriterService).writeOperatorEvent(
                eq(admin.getId()), eq("FORUM_PROHIBITED_PHRASE"), eq(ruleId),
                eq("CREATE_FORUM_PROHIBITED_PHRASE"), eq(null), any()
        );
    }

    @Test
    void update_delegatesToPortAndWritesAudit() {
        UUID ruleId = UuidUtil.generateUuidV7();
        ForumProhibitedPhraseResponse mockResponse = new ForumProhibitedPhraseResponse(
                ruleId, "new", true, 1, admin.getId(), LocalDateTime.now(), LocalDateTime.now()
        );
        when(forumProhibitedPhraseAdminPort.update(eq(admin.getId()), eq(ruleId), any(ForumProhibitedPhraseUpdateRequest.class)))
                .thenReturn(mockResponse);

        var response = service.update(admin.getId(), ruleId, new ForumProhibitedPhraseUpdateRequest("new", 0));

        assertEquals("new", response.phrase());
        verify(forumProhibitedPhraseAdminPort).update(eq(admin.getId()), eq(ruleId), any(ForumProhibitedPhraseUpdateRequest.class));
    }
}
