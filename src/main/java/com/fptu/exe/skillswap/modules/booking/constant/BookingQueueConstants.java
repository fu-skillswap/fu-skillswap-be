package com.fptu.exe.skillswap.modules.booking.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BookingQueueConstants {

    public static final int MAX_PENDING_REQUESTS_PER_SLOT = 3;

    public static final String AUTO_REJECT_SLOT_ACCEPTED_REASON =
            "Mentor đã từ chối booking của bạn vì đã có lịch trình khác.";
}
