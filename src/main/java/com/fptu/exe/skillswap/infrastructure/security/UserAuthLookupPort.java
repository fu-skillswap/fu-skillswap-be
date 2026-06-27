package com.fptu.exe.skillswap.infrastructure.security;

import java.util.Optional;
import java.util.UUID;

public interface UserAuthLookupPort {

    Optional<UserAuthSnapshot> findSnapshotByUserId(UUID userId);
}
