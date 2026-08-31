package com.fptu.exe.skillswap.modules.chat.port;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Immutable entitlement facts consumed by Chat's access policy.  The contract
 * deliberately contains IDs and scalar snapshots only; no booking, identity,
 * course, or mentor aggregate is exposed to Chat.
 */
public interface ChatAccessSnapshotPort {

    List<ChatAccessSnapshot> findChatAccessSnapshots(Collection<UUID> entitlementIds);

    record ChatAccessSnapshot(UUID entitlementId, String status, String completionOutcome,
                              boolean maintainPostSessionChat, LocalDateTime selectedEndTime) {
    }
}
