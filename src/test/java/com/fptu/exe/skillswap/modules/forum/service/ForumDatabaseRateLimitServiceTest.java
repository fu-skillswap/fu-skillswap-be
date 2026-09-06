package com.fptu.exe.skillswap.modules.forum.service;

import com.fptu.exe.skillswap.modules.forum.domain.ForumRateLimitBucket;
import com.fptu.exe.skillswap.modules.forum.repository.ForumRateLimitBucketRepository;
import com.fptu.exe.skillswap.shared.ratelimit.RateLimitExceededException;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForumDatabaseRateLimitServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-05T12:34:56Z");
    private static final LocalDateTime WINDOW_START = LocalDateTime.of(2026, 9, 5, 12, 34);
    private static final LocalDateTime WINDOW_EXPIRATION = LocalDateTime.of(2026, 9, 5, 12, 35);

    @Mock
    private ForumRateLimitBucketRepository bucketRepository;

    @Test
    void limitExceeded_usesSharedBucketAndReturnsRetryAfter() {
        ForumRateLimitBucket bucket = ForumRateLimitBucket.builder()
                .bucketKey("forum:user-1:CREATE_POST")
                .requestCount(5)
                .windowExpiresAt(WINDOW_EXPIRATION)
                .build();
        when(bucketRepository.findByBucketKeyAndWindowStartedAt(any(), any())).thenReturn(Optional.of(bucket));

        ForumDatabaseRateLimitService service = serviceAt(FIXED_INSTANT);
        RateLimitExceededException exception = assertThrows(RateLimitExceededException.class,
                () -> service.check("forum:user-1:CREATE_POST", 5, Duration.ofMinutes(1), "too fast"));

        assertEquals("too fast", exception.getMessage());
        assertTrue(exception.getRetryAfterSeconds() > 0);
        verify(bucketRepository).deleteExpiredForKey(any(), any());
    }

    @Test
    void newWindow_createsBucketWithFirstRequestAndExpiration() {
        when(bucketRepository.findByBucketKeyAndWindowStartedAt(any(), any())).thenReturn(Optional.empty());
        when(bucketRepository.saveAndFlush(any(ForumRateLimitBucket.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        serviceAt(FIXED_INSTANT)
                .check("forum:user-1:CREATE_COMMENT", 20, Duration.ofMinutes(10), "too fast");

        ArgumentCaptor<ForumRateLimitBucket> captor = ArgumentCaptor.forClass(ForumRateLimitBucket.class);
        verify(bucketRepository).saveAndFlush(captor.capture());
        ForumRateLimitBucket saved = captor.getValue();
        assertEquals("forum:user-1:CREATE_COMMENT", saved.getBucketKey());
        assertEquals(1, saved.getRequestCount());
        assertEquals(LocalDateTime.of(2026, 9, 5, 12, 30), saved.getWindowStartedAt());
        assertEquals(LocalDateTime.of(2026, 9, 5, 12, 40), saved.getWindowExpiresAt());
    }

    @Test
    void twoServiceInstances_shareTheSamePersistedCounter() {
        ForumRateLimitBucket bucket = ForumRateLimitBucket.builder()
                .bucketKey("forum:user-1:TOGGLE_REACTION")
                .requestCount(0)
                .windowExpiresAt(WINDOW_EXPIRATION)
                .build();
        when(bucketRepository.findByBucketKeyAndWindowStartedAt(any(), any())).thenReturn(Optional.of(bucket));

        ForumDatabaseRateLimitService firstInstance = serviceAt(FIXED_INSTANT);
        ForumDatabaseRateLimitService secondInstance = serviceAt(FIXED_INSTANT);

        firstInstance.check("forum:user-1:TOGGLE_REACTION", 1, Duration.ofMinutes(1), "too fast");
        assertThrows(RateLimitExceededException.class,
                () -> secondInstance.check("forum:user-1:TOGGLE_REACTION", 1, Duration.ofMinutes(1), "too fast"));
        assertEquals(2, bucket.getRequestCount());
    }

    @Test
    void check_usesIndependentTransactionToPreserveQuotaOnBusinessRollback() throws NoSuchMethodException {
        Method method = ForumDatabaseRateLimitService.class.getDeclaredMethod(
                "check", String.class, int.class, Duration.class, String.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
    }

    @Test
    void differentJvmTimezones_produceTheSameUtcWindow() {
        TimeZone originalTimezone = TimeZone.getDefault();
        when(bucketRepository.findByBucketKeyAndWindowStartedAt(any(), any())).thenReturn(Optional.empty());
        when(bucketRepository.saveAndFlush(any(ForumRateLimitBucket.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        try {
            TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")));
            serviceAt(FIXED_INSTANT).check("forum:user-1:CREATE_POST", 10, Duration.ofMinutes(1), "too fast");

            TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Ho_Chi_Minh")));
            serviceAt(FIXED_INSTANT).check("forum:user-1:CREATE_POST", 10, Duration.ofMinutes(1), "too fast");
        } finally {
            TimeZone.setDefault(originalTimezone);
        }

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDateTime> windowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(bucketRepository, times(2)).findByBucketKeyAndWindowStartedAt(
                keyCaptor.capture(), windowCaptor.capture());
        verify(bucketRepository, times(2)).deleteExpiredForKey(
                org.mockito.ArgumentMatchers.eq("forum:user-1:CREATE_POST"), nowCaptor.capture());
        assertEquals(keyCaptor.getAllValues().get(0), keyCaptor.getAllValues().get(1));
        assertEquals(windowCaptor.getAllValues().get(0), windowCaptor.getAllValues().get(1));
        assertEquals(WINDOW_START, windowCaptor.getAllValues().get(0));
        assertEquals(nowCaptor.getAllValues().get(0), nowCaptor.getAllValues().get(1));
        assertEquals(LocalDateTime.of(2026, 9, 5, 12, 34, 56), nowCaptor.getAllValues().get(0));

        ArgumentCaptor<ForumRateLimitBucket> bucketCaptor = ArgumentCaptor.forClass(ForumRateLimitBucket.class);
        verify(bucketRepository, times(2)).saveAndFlush(bucketCaptor.capture());
        assertEquals(bucketCaptor.getAllValues().get(0).getWindowExpiresAt(),
                bucketCaptor.getAllValues().get(1).getWindowExpiresAt());
        assertEquals(WINDOW_EXPIRATION, bucketCaptor.getAllValues().get(0).getWindowExpiresAt());
    }

    @Test
    void expiredWindow_allowsRequestInTheNextUtcBucket() {
        ForumRateLimitBucket firstBucket = ForumRateLimitBucket.builder()
                .bucketKey("forum:user-1:CREATE_POST")
                .requestCount(1)
                .windowStartedAt(WINDOW_START)
                .windowExpiresAt(WINDOW_EXPIRATION)
                .build();
        Instant nextWindowInstant = Instant.parse("2026-09-05T12:35:01Z");
        when(bucketRepository.findByBucketKeyAndWindowStartedAt(any(), any()))
                .thenAnswer(invocation -> WINDOW_START.equals(invocation.getArgument(1))
                        ? Optional.of(firstBucket) : Optional.empty());
        when(bucketRepository.saveAndFlush(any(ForumRateLimitBucket.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ForumDatabaseRateLimitService service = serviceAt(nextWindowInstant);
        service.check("forum:user-1:CREATE_POST", 1, Duration.ofMinutes(1), "too fast");

        verify(bucketRepository).findByBucketKeyAndWindowStartedAt(
                "forum:user-1:CREATE_POST", LocalDateTime.of(2026, 9, 5, 12, 35));
        verify(bucketRepository).saveAndFlush(any(ForumRateLimitBucket.class));
        assertEquals(1, firstBucket.getRequestCount(), "the old bucket must not be reused after expiration");
    }

    private ForumDatabaseRateLimitService serviceAt(Instant instant) {
        ForumDatabaseRateLimitService service = new ForumDatabaseRateLimitService(bucketRepository);
        service.setTimeProvider(TimeProvider.fixedUtc(instant));
        return service;
    }
}
