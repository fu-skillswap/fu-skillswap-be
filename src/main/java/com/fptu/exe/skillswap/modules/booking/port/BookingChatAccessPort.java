package com.fptu.exe.skillswap.modules.booking.port;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Read-only booking facts required to derive Chat's own access policy. */
public interface BookingChatAccessPort {

    List<ChatAccessSnapshot> findChatAccessSnapshots(Collection<UUID> entitlementIds);

    record ChatAccessSnapshot(UUID entitlementId, String status, String completionOutcome,
                              boolean maintainPostSessionChat, LocalDateTime selectedEndTime) {
    }

}
