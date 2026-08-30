package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.port.MentorRatingPort;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MentorRatingPortImpl implements MentorRatingPort {

    private final MentorProfileRepository mentorProfileRepository;

    @Override
    @Transactional
    public void updateRatingStats(UUID mentorUserId, int newRating) {
        if (mentorUserId == null) {
            return;
        }
        MentorProfile lockedProfile = mentorProfileRepository.findByIdForUpdate(mentorUserId).orElse(null);
        if (lockedProfile == null) {
            return;
        }
        int currentReviewCount = lockedProfile.getTotalReviews() != null ? lockedProfile.getTotalReviews() : 0;
        BigDecimal currentAvg = lockedProfile.getAverageRating() != null ? lockedProfile.getAverageRating() : BigDecimal.ZERO;

        BigDecimal totalScore = currentAvg.multiply(BigDecimal.valueOf(currentReviewCount))
                .add(BigDecimal.valueOf(newRating));
        int newReviewCount = currentReviewCount + 1;
        BigDecimal newAvg = totalScore.divide(BigDecimal.valueOf(newReviewCount), 2, RoundingMode.HALF_UP);

        lockedProfile.setTotalReviews(newReviewCount);
        lockedProfile.setAverageRating(newAvg);
        mentorProfileRepository.save(lockedProfile);
    }
}
