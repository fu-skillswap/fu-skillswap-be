package com.fptu.exe.skillswap.modules.forum.service;

import com.fptu.exe.skillswap.modules.forum.domain.ForumProhibitedPhrase;
import com.fptu.exe.skillswap.modules.forum.repository.ForumProhibitedPhraseRepository;
import com.fptu.exe.skillswap.infrastructure.config.CacheProperties;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ForumProhibitedPhrasePolicyTest {

    @Mock
    private ForumProhibitedPhraseRepository forumProhibitedPhraseRepository;

    @Test
    void rejectComment_normalizesCaseWhitespaceAndZeroWidthCharacters() {
        ForumProhibitedPhrasePolicy policy = new ForumProhibitedPhrasePolicy(forumProhibitedPhraseRepository, new CacheProperties());
        ForumProhibitedPhrase rule = ForumProhibitedPhrase.builder()
                .id(UUID.randomUUID())
                .normalizedPhrase(policy.normalizePhrase("Làm bài thuê"))
                .active(true)
                .build();
        when(forumProhibitedPhraseRepository.findAllByActiveTrueOrderByCreatedAtAscIdAsc())
                .thenReturn(List.of(rule));

        BaseException exception = assertThrows(
                BaseException.class,
                () -> policy.rejectComment("Bạn có nhận LÀM\u200b   BÀI thuê không?")
        );

        assertEquals(ErrorCode.FORUM_CONTENT_PROHIBITED, exception.getErrorCode());
    }

    @Test
    void rejectComment_doesNotMatchPhraseInsideALongerWord() {
        ForumProhibitedPhrasePolicy policy = new ForumProhibitedPhrasePolicy(forumProhibitedPhraseRepository, new CacheProperties());
        ForumProhibitedPhrase rule = ForumProhibitedPhrase.builder()
                .id(UUID.randomUUID())
                .normalizedPhrase(policy.normalizePhrase("bad"))
                .active(true)
                .build();
        when(forumProhibitedPhraseRepository.findAllByActiveTrueOrderByCreatedAtAscIdAsc())
                .thenReturn(List.of(rule));

        assertDoesNotThrow(() -> policy.rejectComment("This classroom is not badger territory."));
    }

    @Test
    void activeRulesAreCachedUntilCommittedMutationInvalidatesTheSnapshot() {
        ForumProhibitedPhrasePolicy policy = new ForumProhibitedPhrasePolicy(forumProhibitedPhraseRepository, new CacheProperties());
        ForumProhibitedPhrase rule = ForumProhibitedPhrase.builder()
                .id(UUID.randomUUID())
                .normalizedPhrase(policy.normalizePhrase("forbidden"))
                .active(true)
                .build();
        when(forumProhibitedPhraseRepository.findAllByActiveTrueOrderByCreatedAtAscIdAsc())
                .thenReturn(List.of(rule));

        assertThrows(BaseException.class, () -> policy.rejectComment("forbidden"));
        assertThrows(BaseException.class, () -> policy.rejectComment("forbidden"));
        verify(forumProhibitedPhraseRepository, times(1)).findAllByActiveTrueOrderByCreatedAtAscIdAsc();

        policy.invalidateActivePhraseCache();
        assertThrows(BaseException.class, () -> policy.rejectComment("forbidden"));
        verify(forumProhibitedPhraseRepository, times(2)).findAllByActiveTrueOrderByCreatedAtAscIdAsc();
    }
}
