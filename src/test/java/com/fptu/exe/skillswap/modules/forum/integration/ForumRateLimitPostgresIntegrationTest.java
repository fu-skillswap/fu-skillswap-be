package com.fptu.exe.skillswap.modules.forum.integration;

import com.fptu.exe.skillswap.ProjectApplication;
import com.fptu.exe.skillswap.infrastructure.storage.StorageLifecycleProperties;
import com.fptu.exe.skillswap.infrastructure.testcontainer.AbstractPostgreSQLIntegrationTest;
import com.fptu.exe.skillswap.modules.forum.domain.ForumRateLimitBucket;
import com.fptu.exe.skillswap.modules.forum.repository.ForumRateLimitBucketRepository;
import com.fptu.exe.skillswap.modules.forum.scheduler.ForumRateLimitBucketCleanupScheduler;
import com.fptu.exe.skillswap.modules.forum.service.ForumDatabaseRateLimitService;
import com.fptu.exe.skillswap.shared.ratelimit.RateLimitExceededException;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real PostgreSQL coverage for row locking, retry and cleanup behavior. */
@ActiveProfiles("test")
@SpringBootTest(
        classes = ProjectApplication.class,
        properties = {
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.test.database.replace=none",
                "application.scheduling.enabled=false",
                "application.storage.lifecycle.cleanup-enabled=true",
                "application.storage.lifecycle.forum-rate-limit-retention-days=1"
        })
class ForumRateLimitPostgresIntegrationTest extends AbstractPostgreSQLIntegrationTest {

    @Autowired
    private ForumRateLimitBucketRepository bucketRepository;

    @Autowired
    private ForumDatabaseRateLimitService rateLimitService;

    @Autowired
    private ForumRateLimitBucketCleanupScheduler cleanupScheduler;

    @Autowired
    private StorageLifecycleProperties properties;

    @Autowired
    private TimeProvider timeProvider;

    @DynamicPropertySource
    static void forcePostgresDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", AbstractPostgreSQLIntegrationTest::getPostgresJdbcUrl);
        registry.add("spring.datasource.username", AbstractPostgreSQLIntegrationTest::getPostgresUsername);
        registry.add("spring.datasource.password", AbstractPostgreSQLIntegrationTest::getPostgresPassword);
        registry.add("spring.datasource.driver-class-name", AbstractPostgreSQLIntegrationTest::getPostgresDriverClassName);
    }

    @BeforeEach
    void cleanBuckets() {
        bucketRepository.deleteAll();
    }

    @AfterEach
    void cleanBucketsAfterTest() {
        bucketRepository.deleteAll();
    }

    @Test
    void oneHundredConcurrentRequests_shareOneLockedBucket() throws Exception {
        String key = "forum:concurrency-user:CREATE_POST";
        int limit = 25;
        int requestCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < requestCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        rateLimitService.check(key, limit, java.time.Duration.ofMinutes(1), "too fast");
                        return true;
                    } catch (RateLimitExceededException ex) {
                        return false;
                    }
                }));
            }
            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();

            int allowed = 0;
            int rejected = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    allowed++;
                } else {
                    rejected++;
                }
            }

            assertEquals(limit, allowed);
            assertEquals(requestCount - limit, rejected);
            assertEquals(1, bucketRepository.findAll().stream()
                    .filter(bucket -> key.equals(bucket.getBucketKey()))
                    .count());
            assertEquals(limit, bucketRepository.findAll().stream()
                    .filter(bucket -> key.equals(bucket.getBucketKey()))
                    .findFirst().orElseThrow().getRequestCount());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void cleanup_removesExpiredBucketsAndKeepsActiveBucket() {
        LocalDateTime now = timeProvider.localDateTime(TimeProvider.UTC_ZONE);
        ForumRateLimitBucket expired = bucketRepository.save(ForumRateLimitBucket.builder()
                .bucketKey("forum:expired:CREATE_POST")
                .windowStartedAt(now.minusDays(2).minusMinutes(1))
                .windowExpiresAt(now.minusDays(2))
                .requestCount(1)
                .build());
        ForumRateLimitBucket active = bucketRepository.save(ForumRateLimitBucket.builder()
                .bucketKey("forum:active:CREATE_POST")
                .windowStartedAt(now.minusMinutes(1))
                .windowExpiresAt(now.plusMinutes(1))
                .requestCount(1)
                .build());

        assertEquals(1, properties.getForumRateLimitRetentionDays());
        cleanupScheduler.executeCleanupJob();

        assertFalse(bucketRepository.existsById(expired.getId()));
        assertTrue(bucketRepository.existsById(active.getId()));
    }
}
