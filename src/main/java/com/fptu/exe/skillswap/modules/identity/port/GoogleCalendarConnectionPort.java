package com.fptu.exe.skillswap.modules.identity.port;

import java.util.UUID;

public interface GoogleCalendarConnectionPort {

    void requireActiveConnectionForServiceCreation(UUID mentorUserId);
}
