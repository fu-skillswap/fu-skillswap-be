package com.fptu.exe.skillswap.modules.filestorage.controller;

import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.infrastructure.storage.StorageProperties;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileStorageControllerTest {

    @Test
    void getUploadUrlIsUnavailableOutsideLocalProfile() {
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
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<StorageGateway> mockStorageGatewayProvider(StorageGateway storageGateway) {
        ObjectProvider<StorageGateway> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(storageGateway);
        return provider;
    }
}
