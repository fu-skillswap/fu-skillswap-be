package com.fptu.exe.skillswap.modules.booking.port;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Read-only entitlement capability for content modules. */
public interface ContentEntitlementQuery {

    boolean hasServiceContentEntitlement(UUID viewerId, UUID serviceId);

    Set<UUID> findUsersWithServiceContentEntitlement(Collection<UUID> serviceIds);
}
