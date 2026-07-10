package com.fptu.exe.skillswap.modules.matching.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fptu.exe.skillswap.modules.matching.domain.MentoringQuestionnaireActivation;
import com.fptu.exe.skillswap.modules.matching.domain.MentoringQuestionnaireAnswer;
import com.fptu.exe.skillswap.modules.matching.repository.MentoringQuestionnaireActivationRepository;
import com.fptu.exe.skillswap.modules.matching.repository.MentoringQuestionnaireAnswerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CachedMenteeMatchingFeatureProviderTest {

    @Mock
    private MentoringQuestionnaireActivationRepository activationRepository;

    @Mock
    private MentoringQuestionnaireAnswerRepository answerRepository;

    private Cache<UUID, MenteeMatchingFeatures> cache;
    private CachedMenteeMatchingFeatureProvider provider;

    @BeforeEach
    void setUp() {
        cache = Caffeine.newBuilder().build();
        provider = new CachedMenteeMatchingFeatureProvider(activationRepository, answerRepository, cache);
    }

    @Test
    void getLatestFeatures_shouldCacheActiveAnswersByUserId() {
        UUID userId = UUID.randomUUID();
        UUID activationId = UUID.randomUUID();
        MentoringQuestionnaireActivation activation = MentoringQuestionnaireActivation.builder()
                .id(activationId)
                .activatedAt(LocalDateTime.now())
                .build();
        when(activationRepository.findFirstByDeactivatedAtIsNullOrderByActivatedAtDesc())
                .thenReturn(Optional.of(activation));
        when(answerRepository.findByActivationIdAndUserId(activationId, userId))
                .thenReturn(List.of(
                        answer(MentoringQuestionnaireDefaults.Q1_FOUNDATION_LEVEL, "FOUNDATION_4", 4),
                        answer(MentoringQuestionnaireDefaults.Q2_OUTPUT_REVIEW_LEVEL, "OUTPUT_REVIEW_3", 3),
                        answer(MentoringQuestionnaireDefaults.Q3_DIRECTION_LEVEL, "DIRECTION_2", 2),
                        answer(MentoringQuestionnaireDefaults.Q4_MENTOR_FIT, "MENTOR_FIT_SUBJECT_MATCH", null),
                        answer(MentoringQuestionnaireDefaults.Q5_DURATION_PREFERENCE, "DURATION_30", 30)
                ));

        MenteeMatchingFeatures first = provider.getLatestFeatures(userId);
        MenteeMatchingFeatures second = provider.getLatestFeatures(userId);

        assertEquals(4, first.foundationNeedLevel());
        assertEquals(3, first.outputReviewNeedLevel());
        assertEquals(2, first.directionNeedLevel());
        assertEquals("MENTOR_FIT_SUBJECT_MATCH", first.mentorFitCode());
        assertEquals("DURATION_30", first.durationPreferenceCode());
        assertSame(first, second);
        verify(answerRepository, times(1)).findByActivationIdAndUserId(activationId, userId);
    }

    @Test
    void getLatestFeatures_fallbackWithUnknownCodes_shouldDegradeGracefully() {
        UUID userId = UUID.randomUUID();
        when(activationRepository.findFirstByDeactivatedAtIsNullOrderByActivatedAtDesc())
                .thenReturn(Optional.empty());
        when(answerRepository.findFirst5ByUserIdOrderByAnsweredAtDesc(userId))
                .thenReturn(List.of(
                        answer("LEGACY_UNKNOWN", "OLD_OPTION", null),
                        answer(MentoringQuestionnaireDefaults.Q1_FOUNDATION_LEVEL, "FOUNDATION_2", 2)
                ));

        MenteeMatchingFeatures features = provider.getLatestFeatures(userId);

        assertEquals(2, features.foundationNeedLevel());
        assertNull(features.outputReviewNeedLevel());
        assertNull(features.directionNeedLevel());
        assertNull(features.mentorFitCode());
        assertNull(features.durationPreferenceCode());
    }

    @Test
    void invalidate_shouldForceReloadOnNextRead() {
        UUID userId = UUID.randomUUID();
        UUID activationId = UUID.randomUUID();
        MentoringQuestionnaireActivation activation = MentoringQuestionnaireActivation.builder()
                .id(activationId)
                .activatedAt(LocalDateTime.now())
                .build();
        when(activationRepository.findFirstByDeactivatedAtIsNullOrderByActivatedAtDesc())
                .thenReturn(Optional.of(activation));
        when(answerRepository.findByActivationIdAndUserId(activationId, userId))
                .thenReturn(List.of(answer(MentoringQuestionnaireDefaults.Q1_FOUNDATION_LEVEL, "FOUNDATION_1", 1)))
                .thenReturn(List.of(answer(MentoringQuestionnaireDefaults.Q1_FOUNDATION_LEVEL, "FOUNDATION_4", 4)));

        MenteeMatchingFeatures first = provider.getLatestFeatures(userId);
        provider.invalidate(userId);
        MenteeMatchingFeatures second = provider.getLatestFeatures(userId);

        assertEquals(1, first.foundationNeedLevel());
        assertEquals(4, second.foundationNeedLevel());
        verify(answerRepository, times(2)).findByActivationIdAndUserId(activationId, userId);
    }

    private MentoringQuestionnaireAnswer answer(String questionCode, String optionCode, Integer scoreValue) {
        return MentoringQuestionnaireAnswer.builder()
                .id(UUID.randomUUID())
                .questionCode(questionCode)
                .optionCode(optionCode)
                .scoreValue(scoreValue)
                .answeredAt(LocalDateTime.now())
                .build();
    }
}
