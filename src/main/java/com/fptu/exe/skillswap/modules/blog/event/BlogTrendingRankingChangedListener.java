package com.fptu.exe.skillswap.modules.blog.event;

import com.fptu.exe.skillswap.modules.blog.service.BlogTrendingCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Invalidates only after the source transaction has committed. */
@Component
@RequiredArgsConstructor
public class BlogTrendingRankingChangedListener {

    private final BlogTrendingCache trendingCache;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRankingChanged(BlogTrendingRankingChangedEvent event) {
        trendingCache.invalidateAfterRankingChange();
    }
}
