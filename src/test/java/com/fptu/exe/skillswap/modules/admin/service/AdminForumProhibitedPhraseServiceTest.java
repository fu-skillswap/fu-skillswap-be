package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.forum.port.ForumProhibitedPhraseAdminPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminForumProhibitedPhraseServiceTest {
    @Mock private ForumProhibitedPhraseAdminPort forumProhibitedPhraseAdminPort;
    @Mock private AdminAuditWriterService adminAuditWriterService;
    @InjectMocks private AdminForumProhibitedPhraseService service;

    @Test
    void list_delegatesToForumAdminPort() {
        when(forumProhibitedPhraseAdminPort.list(true, "cursor", 20)).thenReturn(null);

        assertThat(service.list(true, "cursor", 20)).isNull();

        verify(forumProhibitedPhraseAdminPort).list(true, "cursor", 20);
    }
}
