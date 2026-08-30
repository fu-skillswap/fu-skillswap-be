package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.mentor.port.MentorVerificationAdminPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMentorVerificationModerationServiceTest {
    @Mock private MentorVerificationAdminPort mentorVerificationAdminPort;
    @Mock private AdminAuditWriterService adminAuditWriterService;
    @InjectMocks private AdminMentorVerificationModerationService service;

    @Test
    void approve_delegatesToVerificationAdminPort() {
        UUID adminId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(mentorVerificationAdminPort.approve(adminId, requestId, "approved")).thenReturn(null);

        assertThat(service.approve(adminId, requestId, "approved")).isNull();

        verify(mentorVerificationAdminPort).approve(adminId, requestId, "approved");
    }
}
