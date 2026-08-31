package com.fptu.exe.skillswap.modules.mentor.port;

import java.math.BigDecimal;
import java.util.UUID;

/** Public immutable author projection for blog rendering. */
public record MentorBlogAuthorSummary(UUID mentorUserId, String headline, boolean verified,
                                      BigDecimal averageRating, Integer completedSessions, String bookingCtaLabel) { }
