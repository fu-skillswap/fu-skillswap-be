package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.identity.port.UserAdminPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserModerationServiceTest {
    @Mock private UserAdminPort userAdminPort;
    @Mock private AdminAuditWriterService adminAuditWriterService;
    @InjectMocks private AdminUserModerationService service;

    @Test
    void getVisibleUsers_delegatesToUserAdminPort() {
        when(userAdminPort.getVisibleUsers(isNull())).thenReturn(null);

        assertThat(service.getVisibleUsers(null)).isNull();

        verify(userAdminPort).getVisibleUsers(isNull());
    }
}
