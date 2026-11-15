package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserBanStatusAdapterTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final Cache<UUID, Boolean> cache = Caffeine.newBuilder().build();
    private final UserBanStatusAdapter adapter = new UserBanStatusAdapter(userRepository, cache);

    @Test
    void deletedOrMissingUser_isNotClassifiedAsBanned() {
        UUID deletedUserId = UUID.randomUUID();
        when(userRepository.findById(deletedUserId)).thenReturn(Optional.empty());

        assertFalse(adapter.isBanned(deletedUserId));
    }

    @Test
    void bannedUser_returnsTrue() {
        UUID bannedUserId = UUID.randomUUID();
        when(userRepository.findById(bannedUserId))
                .thenReturn(Optional.of(User.builder().id(bannedUserId).status(UserStatus.BANNED).build()));

        assertTrue(adapter.isBanned(bannedUserId));
    }
}
