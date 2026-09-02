package com.fptu.exe.skillswap.shared.port;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Neutral read-only entitlement capability for content modules. */
public interface ContentEntitlementQuery {

    boolean hasServiceContentEntitlement(UUID viewerId, UUID serviceId);

    Set<UUID> findUsersWithServiceContentEntitlement(Collection<UUID> serviceIds);
}
