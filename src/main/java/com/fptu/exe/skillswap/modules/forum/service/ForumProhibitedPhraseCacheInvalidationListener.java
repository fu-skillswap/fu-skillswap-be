package com.fptu.exe.skillswap.modules.forum.service;

import com.fptu.exe.skillswap.modules.forum.event.ForumProhibitedPhraseChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ForumProhibitedPhraseCacheInvalidationListener {

    private final ForumProhibitedPhrasePolicy prohibitedPhrasePolicy;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChanged(ForumProhibitedPhraseChangedEvent event) {
        prohibitedPhrasePolicy.invalidateActivePhraseCache();
    }
}
