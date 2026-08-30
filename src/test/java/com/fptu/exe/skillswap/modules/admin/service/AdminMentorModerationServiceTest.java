package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.mentor.port.MentorAdminPort;
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
class AdminMentorModerationServiceTest {
    @Mock private MentorAdminPort mentorAdminPort;
    @InjectMocks private AdminMentorModerationService service;

    @Test
    void getMentorDetail_delegatesToMentorAdminPort() {
        UUID mentorUserId = UUID.randomUUID();
        when(mentorAdminPort.getMentorDetail(mentorUserId)).thenReturn(null);

        assertThat(service.getMentorDetail(mentorUserId)).isNull();

        verify(mentorAdminPort).getMentorDetail(mentorUserId);
    }
}
