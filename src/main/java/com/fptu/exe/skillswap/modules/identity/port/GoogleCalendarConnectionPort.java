package com.fptu.exe.skillswap.modules.identity.port;

import java.util.UUID;

public interface GoogleCalendarConnectionPort {

    boolean hasActiveConnection(UUID mentorUserId);

    void requireActiveConnectionForServiceCreation(UUID mentorUserId);
}
