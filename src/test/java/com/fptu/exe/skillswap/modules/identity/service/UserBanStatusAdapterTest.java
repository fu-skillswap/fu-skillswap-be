package com.fptu.exe.skillswap.modules.identity.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserBanStatusAdapterTest {

    @Mock
    private UserRepository userRepository;

    private Cache<UUID, Boolean> userBanStatusCache;
    private UserBanStatusAdapter adapter;

    @BeforeEach
    void setUp() {
        userBanStatusCache = Caffeine.newBuilder().build();
        adapter = new UserBanStatusAdapter(userRepository, userBanStatusCache);
    }

    @Test
    void isBanned_shouldCacheLookupAndInvalidateCorrectly() {
        UUID userId = UUID.randomUUID();

        User activeUser = new User();
        activeUser.setId(userId);
        activeUser.setStatus(UserStatus.ACTIVE);

        User bannedUser = new User();
        bannedUser.setId(userId);
        bannedUser.setStatus(UserStatus.BANNED);

        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser), Optional.of(bannedUser));

        assertFalse(adapter.isBanned(userId));
        assertFalse(adapter.isBanned(userId));
        verify(userRepository, times(1)).findById(userId);

        userBanStatusCache.invalidate(userId);

        assertTrue(adapter.isBanned(userId));
        verify(userRepository, times(2)).findById(userId);
    }
}
