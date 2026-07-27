package com.fptu.exe.skillswap.modules.forum.service;

import com.fptu.exe.skillswap.modules.forum.domain.ForumProhibitedPhrase;
import com.fptu.exe.skillswap.modules.forum.repository.ForumProhibitedPhraseRepository;
import com.fptu.exe.skillswap.infrastructure.config.CacheProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Applies active admin-owned phrase rules without exposing the matching rule to Forum writers. */
@Component
public class ForumProhibitedPhrasePolicy {

    private static final String ACTIVE_PHRASES_CACHE_KEY = "active";

    private final ForumProhibitedPhraseRepository forumProhibitedPhraseRepository;
    private final Cache<String, List<String>> activeNormalizedPhrases;

    @Autowired
    public ForumProhibitedPhrasePolicy(ForumProhibitedPhraseRepository forumProhibitedPhraseRepository,
                                       CacheProperties cacheProperties,
                                       MeterRegistry meterRegistry) {
        this(forumProhibitedPhraseRepository, cacheProperties, meterRegistry, true);
    }

    ForumProhibitedPhrasePolicy(ForumProhibitedPhraseRepository forumProhibitedPhraseRepository,
                                CacheProperties cacheProperties) {
        this(forumProhibitedPhraseRepository, cacheProperties, null, false);
    }

    private ForumProhibitedPhrasePolicy(ForumProhibitedPhraseRepository forumProhibitedPhraseRepository,
                                         CacheProperties cacheProperties,
                                         MeterRegistry meterRegistry,
                                         boolean monitorMetrics) {
        this.forumProhibitedPhraseRepository = forumProhibitedPhraseRepository;
        CacheProperties.TimedCache settings = cacheProperties.getForumProhibitedPhrase();
        this.activeNormalizedPhrases = Caffeine.newBuilder()
                .maximumSize(settings.getMaximumSize())
                .expireAfterWrite(settings.getTtl())
                .recordStats()
                .build();
        if (monitorMetrics) {
            CaffeineCacheMetrics.monitor(meterRegistry, activeNormalizedPhrases, "forum-prohibited-phrase");
        }
    }

    public void rejectPost(String title, String content) {
        rejectIfMatched(join(title, content));
    }

    public void rejectComment(String content) {
        rejectIfMatched(content);
    }

    public String normalizePhrase(String value) {
        if (value == null) {
            return "";
        }
        String compatibilityNormalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(compatibilityNormalized.length());
        boolean pendingWhitespace = false;

        for (int offset = 0; offset < compatibilityNormalized.length(); ) {
            int codePoint = compatibilityNormalized.codePointAt(offset);
            offset += Character.charCount(codePoint);

            int type = Character.getType(codePoint);
            if (Character.isISOControl(codePoint) || type == Character.FORMAT) {
                continue;
            }
            if (Character.isWhitespace(codePoint)) {
                pendingWhitespace = result.length() > 0;
                continue;
            }
            if (pendingWhitespace) {
                result.append(' ');
                pendingWhitespace = false;
            }
            result.appendCodePoint(codePoint);
        }
        return result.toString().trim();
    }

    private void rejectIfMatched(String content) {
        String normalizedContent = normalizePhrase(content);
        if (normalizedContent.isEmpty()) {
            return;
        }
        for (String phrase : activePhrases()) {
            if (matchesPhraseBoundary(normalizedContent, phrase)) {
                throw new BaseException(ErrorCode.FORUM_CONTENT_PROHIBITED);
            }
        }
    }

    public void invalidateActivePhraseCache() {
        activeNormalizedPhrases.invalidate(ACTIVE_PHRASES_CACHE_KEY);
    }

    private List<String> activePhrases() {
        return activeNormalizedPhrases.get(ACTIVE_PHRASES_CACHE_KEY, ignored -> forumProhibitedPhraseRepository
                .findAllByActiveTrueOrderByCreatedAtAscIdAsc()
                .stream()
                .map(ForumProhibitedPhrase::getNormalizedPhrase)
                .filter(phrase -> phrase != null && !phrase.isBlank())
                .toList());
    }

    private boolean matchesPhraseBoundary(String content, String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return false;
        }
        int start = content.indexOf(phrase);
        while (start >= 0) {
            int end = start + phrase.length();
            boolean precededByWord = start > 0 && isWordCharacter(content.codePointBefore(start));
            boolean followedByWord = end < content.length() && isWordCharacter(content.codePointAt(end));
            if (!precededByWord && !followedByWord) {
                return true;
            }
            start = content.indexOf(phrase, start + 1);
        }
        return false;
    }

    private boolean isWordCharacter(int codePoint) {
        return Character.isLetterOrDigit(codePoint);
    }

    private String join(String title, String content) {
        return (title == null ? "" : title) + " " + (content == null ? "" : content);
    }
}
