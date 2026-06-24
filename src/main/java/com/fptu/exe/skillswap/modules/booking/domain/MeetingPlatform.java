package com.fptu.exe.skillswap.modules.booking.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Meeting platform selected by the mentor for an accepted booking.")
public enum MeetingPlatform {
    GOOGLE_MEET,
    ZOOM,
    MICROSOFT_TEAMS,
    DISCORD,
    OFFLINE,
    OTHER
}
