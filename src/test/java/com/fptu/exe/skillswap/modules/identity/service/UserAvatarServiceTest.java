package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.modules.filestorage.port.PublicAssetUploadPort;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.dto.response.UserAvatarResponse;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAvatarServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PublicAssetUploadPort publicAssetUploadPort;

    @InjectMocks
    private UserAvatarService userAvatarService;

    @Test
    void confirmAndUpdateAvatar_shouldUpdateAvatarUrlAndFileId() {
        UUID userId = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        String newUrl = "https://cdn.skillswap.asia/public-assets/avatars/" + userId + "/new.png";

        User user = User.builder()
                .id(userId)
                .email("user@fpt.edu.vn")
                .fullName("User")
                .avatarUrl("https://lh3.googleusercontent.com/mail-pic")
                .oauthAvatarUrl("https://lh3.googleusercontent.com/mail-pic")
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(publicAssetUploadPort.confirmAvatar(userId, intentId))
                .thenReturn(new PublicAssetUploadPort.FileAssetMetadata(assetId, newUrl, "image/png", 2048L));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAvatarResponse response = userAvatarService.confirmAndUpdateAvatar(userId, intentId);

        assertEquals(userId, response.userId());
        assertEquals(assetId, response.avatarAssetId());
        assertEquals(newUrl, response.avatarUrl());
        assertFalse(response.restoredFromMail());
        assertEquals(newUrl, user.getAvatarUrl());
        assertEquals(assetId, user.getAvatarFileId());
        assertEquals("https://lh3.googleusercontent.com/mail-pic", user.getOauthAvatarUrl());
        verify(userRepository).save(user);
    }

    @Test
    void removeAvatar_withGoogleMailAvatar_shouldRestoreMailAvatar() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("user@fpt.edu.vn")
                .fullName("User")
                .avatarUrl("https://cdn.skillswap.asia/public-assets/avatars/" + userId + "/custom.png")
                .avatarFileId(UUID.randomUUID())
                .oauthAvatarUrl("https://lh3.googleusercontent.com/mail-pic")
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAvatarResponse response = userAvatarService.removeAvatar(userId);

        assertNull(response.avatarAssetId());
        assertEquals("https://lh3.googleusercontent.com/mail-pic", response.avatarUrl());
        assertTrue(response.restoredFromMail());
        assertNull(user.getAvatarFileId());
        assertEquals("https://lh3.googleusercontent.com/mail-pic", user.getAvatarUrl());
    }

    @Test
    void removeAvatar_withoutMailAvatar_shouldClearToNull() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("user@fpt.edu.vn")
                .fullName("User")
                .avatarUrl("https://cdn.skillswap.asia/public-assets/avatars/" + userId + "/custom.png")
                .avatarFileId(UUID.randomUUID())
                .oauthAvatarUrl(null)
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAvatarResponse response = userAvatarService.removeAvatar(userId);

        assertNull(response.avatarAssetId());
        assertNull(response.avatarUrl());
        assertFalse(response.restoredFromMail());
    }
}
