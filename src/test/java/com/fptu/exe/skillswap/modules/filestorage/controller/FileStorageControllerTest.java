package com.fptu.exe.skillswap.modules.filestorage.controller;

import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.infrastructure.storage.StorageProperties;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileStorageControllerTest {

    @Test
    void getUploadUrlReturnsStorageErrorWhenGatewayIsNotConfigured() {
        ObjectProvider<StorageGateway> storageGatewayProvider = mockStorageGatewayProvider(null);
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setAllowedContentTypes(List.of("image/jpeg", "image/png", "application/pdf"));
        Environment environment = mock(Environment.class);
        FileStorageController controller = new FileStorageController(
                storageGatewayProvider,
                storageProperties,
                environment
        );

        UserPrincipal principal = UserPrincipal.create(UUID.randomUUID(), "user@test.com", List.of(RoleCode.MENTEE));
        assertThatThrownBy(() -> controller.getUploadUrl(principal, "proof.png", "image/png"))
                .isInstanceOfSatisfying(BaseException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STORAGE_ERROR);
                    assertThat(exception.getMessage()).contains("chưa cấu hình storage");
                });
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<StorageGateway> mockStorageGatewayProvider(StorageGateway storageGateway) {
        ObjectProvider<StorageGateway> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(storageGateway);
        return provider;
    }
}
