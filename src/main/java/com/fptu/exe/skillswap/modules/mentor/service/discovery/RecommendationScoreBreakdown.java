package com.fptu.exe.skillswap.modules.mentor.service.discovery;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record RecommendationScoreBreakdown(
        BigDecimal academicScore,
        BigDecimal qualityScore,
        BigDecimal capabilityScore,
        BigDecimal serviceScore,
        BigDecimal availabilityScore,
        BigDecimal durationScore,
        BigDecimal totalRawScore,
        BigDecimal percentageScore
) {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    public RecommendationScoreBreakdown {
        academicScore = normalize(academicScore, "academicScore");
        qualityScore = normalize(qualityScore, "qualityScore");
        capabilityScore = normalize(capabilityScore, "capabilityScore");
        serviceScore = normalize(serviceScore, "serviceScore");
        availabilityScore = normalize(availabilityScore, "availabilityScore");
        durationScore = normalize(durationScore, "durationScore");
        totalRawScore = normalize(totalRawScore, "totalRawScore");
        percentageScore = normalize(percentageScore, "percentageScore");
    }

    public static RecommendationScoreBreakdown empty() {
        return new RecommendationScoreBreakdown(ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);
    }

    public BigDecimal componentTotal() {
        return academicScore
                .add(qualityScore)
                .add(capabilityScore)
                .add(serviceScore)
                .add(availabilityScore)
                .add(durationScore)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalize(BigDecimal value, String fieldName) {
        if (value == null) {
            throw new NullPointerException(fieldName + " must not be null");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
