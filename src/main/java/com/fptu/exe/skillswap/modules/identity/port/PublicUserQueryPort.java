package com.fptu.exe.skillswap.modules.identity.port;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Public, entity-free identity read contract for feature modules.
 * No JPA aggregate or identity enum crosses this boundary.
 */
public interface PublicUserQueryPort {

    Optional<UserSummaryRecord> findUserSummaryById(UUID userId);

    Map<UUID, UserSummaryRecord> findUserSummariesByIdIn(Collection<UUID> userIds);
}
