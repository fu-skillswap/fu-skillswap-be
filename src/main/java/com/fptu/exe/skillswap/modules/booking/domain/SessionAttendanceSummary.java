package com.fptu.exe.skillswap.modules.booking.domain;

/** Compact attendance evidence exposed to participants and administrators. */
public enum SessionAttendanceSummary {
    NONE,
    MENTOR_ONLY,
    MENTEE_ONLY,
    BOTH;

    public static SessionAttendanceSummary from(boolean mentorCheckedIn, boolean menteeCheckedIn) {
        if (mentorCheckedIn && menteeCheckedIn) {
            return BOTH;
        }
        if (mentorCheckedIn) {
            return MENTOR_ONLY;
        }
        if (menteeCheckedIn) {
            return MENTEE_ONLY;
        }
        return NONE;
    }
}
