package com.fptu.exe.skillswap.modules.booking.port;

import com.fptu.exe.skillswap.modules.chat.port.ChatAccessSnapshotPort;

/** Read-only booking facts required to derive Chat's own access policy. */
public interface BookingChatAccessPort extends ChatAccessSnapshotPort {

}
