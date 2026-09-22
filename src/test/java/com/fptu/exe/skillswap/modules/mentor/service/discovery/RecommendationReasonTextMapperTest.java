package com.fptu.exe.skillswap.modules.mentor.service.discovery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecommendationReasonTextMapperTest {

    @Test
    void shouldPreserveDynamicActiveServiceText() {
        assertEquals(
                "Có 3 dịch vụ đang hoạt động",
                RecommendationReasonTextMapper.toVietnamese(
                        RecommendationReason.counted(RecommendationReasonCode.ACTIVE_SERVICES, 3)
                )
        );
    }

    @Test
    void shouldMapStableReasonText() {
        assertEquals(
                "Được đánh giá cao từ mentee",
                RecommendationReasonTextMapper.toVietnamese(
                        RecommendationReason.of(RecommendationReasonCode.HIGH_RATING)
                )
        );
    }
}
