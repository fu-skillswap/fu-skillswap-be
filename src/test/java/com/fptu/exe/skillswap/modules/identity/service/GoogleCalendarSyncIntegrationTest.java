package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.identity.domain.GoogleCalendarConnection;
import com.fptu.exe.skillswap.modules.identity.domain.GoogleCalendarConnectionStatus;
import com.fptu.exe.skillswap.modules.identity.domain.GoogleCalendarSyncJob;
import com.fptu.exe.skillswap.modules.identity.domain.GoogleCalendarSyncJobStatus;
import com.fptu.exe.skillswap.modules.identity.domain.GoogleCalendarSyncJobType;
import com.fptu.exe.skillswap.modules.identity.repository.GoogleCalendarConnectionRepository;
import com.fptu.exe.skillswap.modules.identity.repository.GoogleCalendarSyncJobRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
class GoogleCalendarSyncIntegrationTest {

    @Autowired
    private GoogleCalendarSyncService syncService;

    @Autowired
    private GoogleCalendarSyncJobRepository jobRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MentorProfileRepository mentorProfileRepository;

    @Autowired
    private GoogleCalendarConnectionRepository connectionRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockBean
    private GoogleCalendarApiClient apiClient;

    private User mentorUser;
    private MentorProfile mentorProfile;
    private User menteeUser;
    private Booking booking;

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(status -> {
            mentorUser = userRepository.save(User.builder()
                    .email("sync-mentor-" + UUID.randomUUID() + "@test.com")
                    .fullName("Mentor Sync")
                    .status(UserStatus.ACTIVE)
                    .build());

            mentorProfile = mentorProfileRepository.save(MentorProfile.builder()
                    .user(mentorUser)
                    .status(MentorStatus.ACTIVE)
                    .isAvailable(true)
                    .build());

            menteeUser = userRepository.save(User.builder()
                    .email("sync-mentee-" + UUID.randomUUID() + "@test.com")
                    .fullName("Mentee Sync")
                    .status(UserStatus.ACTIVE)
                    .build());

            booking = bookingRepository.save(Booking.builder()
                    .mentee(menteeUser)
                    .mentorProfile(mentorProfile)
                    .status(BookingStatus.ACCEPTED_AWAITING_PAYMENT)
                    .learningGoalTitle("Test Sync Booking")
                    .selectedStartTime(DateTimeUtil.now().plusDays(1))
                    .selectedEndTime(DateTimeUtil.now().plusDays(1).plusHours(1))
                    .build());

            connectionRepository.save(GoogleCalendarConnection.builder()
                    .user(mentorUser)
                    .connectionStatus(GoogleCalendarConnectionStatus.ACTIVE)
                    .accessTokenCiphertext("ENCRYPTED_ACCESS_TOKEN")
                    .refreshTokenCiphertext("ENCRYPTED_REFRESH_TOKEN")
                    .tokenExpiresAt(DateTimeUtil.now().plusHours(1))
                    .calendarId("primary")
                    .keyVersion(1)
                    .googleEmail("test@test.com")
                    .googleSubject("subject123")
                    .build());
        });
    }

    @Test
    void processSingleJob_whenGoogleApiFails_shouldMarkAsRetryable() {
        GoogleCalendarSyncJob job = jobRepository.save(GoogleCalendarSyncJob.builder()
                .bookingId(booking.getId())
                .mentorUserId(mentorUser.getId())
                .jobType(GoogleCalendarSyncJobType.CREATE_BOOKING_EVENT)
                .status(GoogleCalendarSyncJobStatus.PENDING)
                .idempotencyKey("TEST_SYNC_1_" + UUID.randomUUID())
                .attemptCount(0)
                .runAfter(DateTimeUtil.now())
                .build());

        when(apiClient.createBookingEvent(any(), any(), any(), any(), any()))
                .thenThrow(new GoogleCalendarApiClient.GoogleCalendarTransientException("500", "Google Internal Error"));

        syncService.processSingleJob(job.getId());

        GoogleCalendarSyncJob updatedJob = jobRepository.findById(job.getId()).orElseThrow();
        assertEquals(GoogleCalendarSyncJobStatus.RETRYING, updatedJob.getStatus());
        assertEquals(1, updatedJob.getAttemptCount());
        assertEquals("500", updatedJob.getLastErrorCode());
    }

    @Test
    void processSingleJob_concurrentExecution_onlyProcessesOnce() throws InterruptedException {
        GoogleCalendarSyncJob job = jobRepository.save(GoogleCalendarSyncJob.builder()
                .bookingId(booking.getId())
                .mentorUserId(mentorUser.getId())
                .jobType(GoogleCalendarSyncJobType.CREATE_BOOKING_EVENT)
                .status(GoogleCalendarSyncJobStatus.PENDING)
                .idempotencyKey("TEST_SYNC_2_" + UUID.randomUUID())
                .build());

        when(apiClient.createBookingEvent(any(), any(), any(), any(), any()))
                .thenReturn(new GoogleCalendarApiClient.GoogleCalendarEventResponse("event_id", "https://meet.google.com/abc", "etag1"));

        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    syncService.processSingleJob(job.getId());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);

        // Verify the API was only called once, meaning concurrency control (status update) worked
        verify(apiClient, times(1)).createBookingEvent(any(), any(), any(), any(), any());

        GoogleCalendarSyncJob updatedJob = jobRepository.findById(job.getId()).orElseThrow();
        assertEquals(GoogleCalendarSyncJobStatus.SUCCEEDED, updatedJob.getStatus());
    }
}
