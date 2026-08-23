package com.fptu.exe.skillswap.modules.payment.integration;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.dto.request.CancelBookingRequest;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.service.BookingService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.payment.domain.CreditLedgerAccount;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerSourceType;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentAttempt;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentAttemptStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentTargetType;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentSettlementStatus;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentWebhookRequest;
import com.fptu.exe.skillswap.infrastructure.testcontainer.AbstractPostgreSQLIntegrationTest;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.payment.integration.payos.PayOsGateway;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentAttemptRepository;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.modules.payment.service.CreditLedgerService;
import com.fptu.exe.skillswap.modules.payment.service.PaymentOrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
public class PaymentConcurrencyIntegrationTest extends AbstractPostgreSQLIntegrationTest {

    @Autowired
    private PaymentOrderService paymentOrderService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreditLedgerService creditLedgerService;

    @Autowired
    private com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository mentorProfileRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private PayOsGateway payOsGateway;

    private User mentee;
    private User mentor;
    private Booking booking;
    private PaymentOrder order;
    private PaymentAttempt surplusAttempt;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate.execute(status -> {
            mentee = userRepository.save(User.builder()
                    .fullName("Concurrent Mentee")
                    .email("c.mentee@test.com")
                    .status(com.fptu.exe.skillswap.modules.identity.domain.UserStatus.ACTIVE)
                    .build());

            mentor = userRepository.save(User.builder()
                    .fullName("Concurrent Mentor")
                    .email("c.mentor@test.com")
                    .status(com.fptu.exe.skillswap.modules.identity.domain.UserStatus.ACTIVE)
                    .build());

            com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile mentorProfile = com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile.builder()
                    .user(mentor)
                    .status(com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus.ACTIVE)
                    .verifiedAt(LocalDateTime.now().minusDays(1))
                    .isAvailable(true)
                    .headline("Spring Boot Mentor")
                    .expertiseDescription("Hỗ trợ Java backend và thiết kế REST API.")
                    .foundationSupportLevel(3)
                    .outputReviewSupportLevel(3)
                    .directionSupportLevel(2)
                    .teachingMode(com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode.ONLINE)
                    .sessionDuration(60)
                    .build();
            mentorProfile = mentorProfileRepository.save(mentorProfile);

            booking = Booking.builder()
                    .id(UUID.randomUUID())
                    .mentee(mentee)
                    .mentorProfile(mentorProfile)
                    .status(BookingStatus.ACCEPTED_AWAITING_PAYMENT)
                    .learningGoalTitle("Test Booking")
                    .learningGoalDescription("Test Description")
                    .serviceIsFreeSnapshot(false)
                    .serviceDurationSnapshot(60)
                    .servicePriceScoinSnapshot(100_000)
                    .acceptedAt(LocalDateTime.now())
                    .selectedStartTime(LocalDateTime.now().plusDays(1))
                    .selectedEndTime(LocalDateTime.now().plusDays(1).plusHours(1))
                    .build();
            booking = bookingRepository.save(booking);

            // Pre-create the credit account
            creditLedgerService.ensureUserAccount(mentee.getId());

            order = PaymentOrder.builder()
                    .id(UUID.randomUUID())
                    .orderCode("PAY-CONC-1")
                    .targetType(PaymentTargetType.BOOKING).targetId(booking.getId())
                    .providerOrderCode("111")
                    .payerUserId(mentee.getId())
                    .mentorUserId(mentor.getId())
                    .grossScoin(100_000)
                    .remainingPayableScoin(0)
                    .status(PaymentOrderStatus.PAID)
                    .build();
            order = paymentOrderRepository.save(order);

            surplusAttempt = PaymentAttempt.builder()
                    .id(UUID.randomUUID())
                    .paymentOrderId(order.getId())
                    .attemptNo(2)
                    .status(PaymentAttemptStatus.REDIRECTED)
                    .providerOrderCode("999999")
                    .build();
            surplusAttempt = paymentAttemptRepository.save(surplusAttempt);
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM google_calendar_sync_jobs");
        jdbcTemplate.execute("DELETE FROM domain_event_outbox");
        jdbcTemplate.execute("DELETE FROM email_outbox");
        jdbcTemplate.execute("DELETE FROM notifications");
        jdbcTemplate.execute("DELETE FROM conversation_booking_links");
        jdbcTemplate.execute("DELETE FROM conversation_participants");
        jdbcTemplate.execute("DELETE FROM conversations");
        jdbcTemplate.execute("DELETE FROM sessions");
        jdbcTemplate.execute("DELETE FROM booking_events");
        jdbcTemplate.execute("DELETE FROM mentor_violation_events");
        jdbcTemplate.execute("DELETE FROM settlement_entries");
        jdbcTemplate.execute("DELETE FROM settlement_accounts");
        jdbcTemplate.execute("DELETE FROM credit_ledger_entries");
        jdbcTemplate.execute("DELETE FROM credit_ledger_accounts");
        jdbcTemplate.execute("DELETE FROM payment_attempts");
        jdbcTemplate.execute("DELETE FROM payment_orders");
        jdbcTemplate.execute("DELETE FROM bookings");
        jdbcTemplate.execute("DELETE FROM mentor_profiles");
        jdbcTemplate.execute("DELETE FROM users");
    }

    @Test
    void handleWebhook_concurrentSurplusPayment_shouldIssueCreditOnlyOnce() throws InterruptedException {
        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);

        // Mock PayOsGateway to return verified webhook
        PayOsGateway.VerifiedWebhook verifiedWebhook = new PayOsGateway.VerifiedWebhook(
                "999999",
                "pl-123",
                "evt-123",
                "txn-123",
                "PAID",
                true,
                LocalDateTime.now(),
                100_000L
        );
        when(payOsGateway.verifyWebhook(any())).thenReturn(verifiedWebhook);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    PaymentWebhookRequest req = new PaymentWebhookRequest(
                            "00", "Success", true,
                            new PaymentWebhookRequest.PaymentWebhookDataRequest(
                                    999999L, 100_000L, "description", "123123", "REF", "TXN",
                                    "VND", "pl-123", "00", "Success", "cb", "cbn", "cn", "can", "van", "vanr"
                            ),
                            "sig"
                    );
                    paymentOrderService.handleWebhook(req);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Ignore exceptions from duplicate event IDs, optimistic locks, etc.
                } finally {
                    done.countDown();
                }
            });
        }

        latch.countDown();
        done.await(10, TimeUnit.SECONDS);

        int balance = creditLedgerService.getAvailableBalance(mentee.getId());
        assertEquals(100_000, balance, "Credit should be exactly 100,000 SCoin regardless of concurrent webhooks.");

        PaymentAttempt attemptInDb = paymentAttemptRepository.findById(surplusAttempt.getId()).orElseThrow();
        assertEquals(PaymentAttemptStatus.SUCCEEDED_SURPLUS, attemptInDb.getStatus(), "Attempt status should be SUCCEEDED_SURPLUS");
    }

    @Test
    void paidWebhookAndMenteeCancel_concurrent_shouldSerializeWithoutDeadlockAndRefundOnce() throws Exception {
        transactionTemplate.execute(status -> {
            order.setStatus(PaymentOrderStatus.AWAITING_PROVIDER_PAYMENT);
            order.setRemainingPayableScoin(100_000);
            order.setSettlementStatus(null);
            paymentOrderRepository.save(order);
            surplusAttempt.setAttemptNo(1);
            surplusAttempt.setStatus(PaymentAttemptStatus.REDIRECTED);
            paymentAttemptRepository.save(surplusAttempt);
            return null;
        });

        when(payOsGateway.verifyWebhook(any())).thenReturn(new PayOsGateway.VerifiedWebhook(
                "999999", "pl-race", "evt-race", "txn-race", "PAID", true,
                LocalDateTime.now(), 100_000L));
        PaymentWebhookRequest webhookRequest = new PaymentWebhookRequest(
                "00", "Success", true,
                new PaymentWebhookRequest.PaymentWebhookDataRequest(
                        999999L, 100_000L, "booking payment", "123123", "REF-RACE", "TXN-RACE",
                        "VND", "pl-race", "00", "Success", "cb", "cbn", "cn", "can", "van", "vanr"),
                "sig");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> webhookFailure = new AtomicReference<>();
        AtomicReference<Throwable> cancelFailure = new AtomicReference<>();
        try {
            var webhookFuture = executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    retryWithBackoff(() -> paymentOrderService.handleWebhook(webhookRequest), 3);
                } catch (Throwable throwable) {
                    webhookFailure.set(throwable);
                }
            });
            var cancelFuture = executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    retryWithBackoff(() -> bookingService.cancelBookingByMentee(
                            mentee.getId(), booking.getId(), new CancelBookingRequest("Changed plan")), 3);
                } catch (Throwable throwable) {
                    cancelFailure.set(throwable);
                }
            });

            org.junit.jupiter.api.Assertions.assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            webhookFuture.get(15, TimeUnit.SECONDS);
            cancelFuture.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        org.junit.jupiter.api.Assertions.assertNull(webhookFailure.get(), "Webhook must complete without deadlock");
        org.junit.jupiter.api.Assertions.assertNull(cancelFailure.get(), "Cancellation must complete without deadlock");
        Booking storedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        PaymentOrder storedOrder = paymentOrderRepository.findById(order.getId()).orElseThrow();
        assertEquals(BookingStatus.CANCELLED_BY_MENTEE, storedBooking.getStatus());
        assertEquals(PaymentOrderStatus.PAID, storedOrder.getStatus());
        assertEquals(PaymentSettlementStatus.REFUNDED, storedOrder.getSettlementStatus());
        assertEquals(100_000, creditLedgerService.getAvailableBalance(mentee.getId()));
    }

    private void retryWithBackoff(Runnable operation, int maxRetries) throws Throwable {
        Throwable lastException = null;
        for (int i = 0; i < maxRetries; i++) {
            try {
                operation.run();
                return;
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                lastException = e;
                if (i < maxRetries - 1) {
                    long backoffMs = (long) Math.pow(2, i) * 100;
                    Thread.sleep(backoffMs);
                }
            }
        }
        if (lastException != null) {
            throw lastException;
        }
    }
}
