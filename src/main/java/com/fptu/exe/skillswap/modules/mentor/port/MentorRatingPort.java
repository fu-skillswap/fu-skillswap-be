package com.fptu.exe.skillswap.modules.mentor.port;

import java.util.UUID;

public interface MentorRatingPort {
    void updateRatingStats(UUID mentorUserId, int newRating);
}
