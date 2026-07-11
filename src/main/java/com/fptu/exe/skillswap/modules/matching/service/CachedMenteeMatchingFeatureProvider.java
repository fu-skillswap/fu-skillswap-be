package com.fptu.exe.skillswap.modules.matching.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.fptu.exe.skillswap.modules.matching.domain.MentoringQuestionnaireActivation;
import com.fptu.exe.skillswap.modules.matching.domain.MentoringQuestionnaireAnswer;
import com.fptu.exe.skillswap.modules.matching.repository.MentoringQuestionnaireActivationRepository;
import com.fptu.exe.skillswap.modules.matching.repository.MentoringQuestionnaireAnswerRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CachedMenteeMatchingFeatureProvider implements MenteeMatchingFeatureProvider {

    private final MentoringQuestionnaireActivationRepository activationRepository;
    private final MentoringQuestionnaireAnswerRepository answerRepository;
    private final Cache<UUID, MenteeMatchingFeatures> menteeMatchingFeaturesCache;

    @Override
    @Transactional(readOnly = true)
    public MenteeMatchingFeatures getLatestFeatures(UUID userId) {
        requireUserId(userId);
        MenteeMatchingFeatures cached = menteeMatchingFeaturesCache.getIfPresent(userId);
        if (cached != null) {
            return cached;
        }

        MenteeMatchingFeatures features = loadLatestFeatures(userId);
        menteeMatchingFeaturesCache.put(userId, features);
        return features;
    }

    MenteeMatchingFeatures loadLatestFeatures(UUID userId) {
        MentoringQuestionnaireActivation activation = activationRepository.findFirstByDeactivatedAtIsNullOrderByActivatedAtDesc().orElse(null);
        if (activation != null) {
            return normalizeActiveAnswers(answerRepository.findByActivationIdAndUserId(activation.getId(), userId));
        }
        return normalizeFallbackAnswers(answerRepository.findFirst5ByUserIdOrderByAnsweredAtDesc(userId), userId);
    }

    void invalidate(UUID userId) {
        if (userId != null) {
            menteeMatchingFeaturesCache.invalidate(userId);
        }
    }

    void invalidateAll() {
        menteeMatchingFeaturesCache.invalidateAll();
    }

    private MenteeMatchingFeatures normalizeActiveAnswers(List<MentoringQuestionnaireAnswer> answers) {
        Map<String, MentoringQuestionnaireAnswer> byCode = newestAnswerByCode(answers);
        return new MenteeMatchingFeatures(
                score(byCode, MentoringQuestionnaireDefaults.Q1_FOUNDATION_LEVEL),
                score(byCode, MentoringQuestionnaireDefaults.Q2_OUTPUT_REVIEW_LEVEL),
                score(byCode, MentoringQuestionnaireDefaults.Q3_DIRECTION_LEVEL),
                option(byCode, MentoringQuestionnaireDefaults.Q4_MENTOR_FIT),
                option(byCode, MentoringQuestionnaireDefaults.Q5_DURATION_PREFERENCE),
                latestAnsweredAt(answers)
        );
    }

    private MenteeMatchingFeatures normalizeFallbackAnswers(List<MentoringQuestionnaireAnswer> answers, UUID userId) {
        if (answers == null || answers.isEmpty()) {
            return new MenteeMatchingFeatures(null, null, null, null, null, null);
        }

        Map<String, MentoringQuestionnaireAnswer> byCode = answers.stream()
                .filter(answer -> answer.getQuestionCode() != null)
                .sorted(Comparator.comparing(MentoringQuestionnaireAnswer::getAnsweredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toMap(
                        MentoringQuestionnaireAnswer::getQuestionCode,
                        Function.identity(),
                        (left, right) -> left
                ));

        answers.stream()
                .map(MentoringQuestionnaireAnswer::getQuestionCode)
                .filter(code -> code != null && !isSupportedQuestionCode(code))
                .distinct()
                .forEach(code -> log.warn("Ignoring unsupported fallback matching answer code={} for userId={} source=fallback", code, userId));

        return new MenteeMatchingFeatures(
                safeLevel(byCode, MentoringQuestionnaireDefaults.Q1_FOUNDATION_LEVEL, userId),
                safeLevel(byCode, MentoringQuestionnaireDefaults.Q2_OUTPUT_REVIEW_LEVEL, userId),
                safeLevel(byCode, MentoringQuestionnaireDefaults.Q3_DIRECTION_LEVEL, userId),
                safeCategorical(byCode, MentoringQuestionnaireDefaults.Q4_MENTOR_FIT, userId),
                safeCategorical(byCode, MentoringQuestionnaireDefaults.Q5_DURATION_PREFERENCE, userId),
                latestAnsweredAt(answers)
        );
    }

    private Map<String, MentoringQuestionnaireAnswer> newestAnswerByCode(List<MentoringQuestionnaireAnswer> answers) {
        return answers.stream()
                .filter(answer -> answer.getQuestionCode() != null)
                .sorted(Comparator.comparing(MentoringQuestionnaireAnswer::getAnsweredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toMap(
                        MentoringQuestionnaireAnswer::getQuestionCode,
                        Function.identity(),
                        (left, right) -> left
                ));
    }

    private Integer safeLevel(Map<String, MentoringQuestionnaireAnswer> byCode, String code, UUID userId) {
        MentoringQuestionnaireAnswer answer = byCode.get(code);
        if (answer == null) {
            return null;
        }
        Integer score = answer.getScoreValue();
        if (score == null || score < 1 || score > 4) {
            log.warn("Ignoring malformed fallback level answer code={} optionCode={} userId={} source=fallback", code, answer.getOptionCode(), userId);
            return null;
        }
        return score;
    }

    private String safeCategorical(Map<String, MentoringQuestionnaireAnswer> byCode, String code, UUID userId) {
        MentoringQuestionnaireAnswer answer = byCode.get(code);
        if (answer == null) {
            return null;
        }
        String optionCode = answer.getOptionCode();
        if (optionCode == null || optionCode.isBlank()) {
            log.warn("Ignoring malformed fallback categorical answer code={} userId={} source=fallback", code, userId);
            return null;
        }
        return optionCode;
    }

    private boolean isSupportedQuestionCode(String code) {
        return MentoringQuestionnaireDefaults.Q1_FOUNDATION_LEVEL.equals(code)
                || MentoringQuestionnaireDefaults.Q2_OUTPUT_REVIEW_LEVEL.equals(code)
                || MentoringQuestionnaireDefaults.Q3_DIRECTION_LEVEL.equals(code)
                || MentoringQuestionnaireDefaults.Q4_MENTOR_FIT.equals(code)
                || MentoringQuestionnaireDefaults.Q5_DURATION_PREFERENCE.equals(code);
    }

    private Integer score(Map<String, MentoringQuestionnaireAnswer> answers, String code) {
        MentoringQuestionnaireAnswer answer = answers.get(code);
        return answer == null ? null : answer.getScoreValue();
    }

    private String option(Map<String, MentoringQuestionnaireAnswer> answers, String code) {
        MentoringQuestionnaireAnswer answer = answers.get(code);
        return answer == null ? null : answer.getOptionCode();
    }

    private LocalDateTime latestAnsweredAt(List<MentoringQuestionnaireAnswer> answers) {
        return answers.stream()
                .map(MentoringQuestionnaireAnswer::getAnsweredAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    private void requireUserId(UUID userId) {
        if (userId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu userId để lấy matching features");
        }
    }
}
