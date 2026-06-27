package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.infrastructure.security.UserAuthLookupPort;
import com.fptu.exe.skillswap.infrastructure.security.UserAuthSnapshot;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAuthLookupAdapter implements UserAuthLookupPort {

    private final UserRepository userRepository;

    @Override
    public Optional<UserAuthSnapshot> findSnapshotByUserId(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }

        return userRepository.findById(userId)
                .map(user -> new UserAuthSnapshot(
                        user.getId(),
                        user.getEmail(),
                        new ArrayList<>(user.getRoles() == null ? java.util.List.of() : user.getRoles())
                ));
    }
}
