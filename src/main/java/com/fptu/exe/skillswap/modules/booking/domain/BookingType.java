package com.fptu.exe.skillswap.modules.booking.domain;

/** Public booking shape. The persisted discriminator is the nullable group-session relation. */
public enum BookingType {
    ONE_TO_ONE,
    GROUP_SESSION
}
