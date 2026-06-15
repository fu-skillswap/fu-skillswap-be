package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.infrastructure.security.UserBanStatusPort;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserBanStatusAdapter implements UserBanStatusPort {

    private final UserRepository userRepository;

    @Override
    public boolean isBanned(UUID userId) {
        return userRepository.findById(userId)
                .map(user -> user.getStatus() == UserStatus.BANNED)
                .orElse(true);
    }
}
