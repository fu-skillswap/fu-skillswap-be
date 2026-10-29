package com.fptu.exe.skillswap.modules.payment.integration;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.payment.domain.Coupon;
import com.fptu.exe.skillswap.modules.payment.domain.CouponDiscountType;
import com.fptu.exe.skillswap.modules.payment.domain.CouponRedemption;
import com.fptu.exe.skillswap.modules.payment.domain.CouponRedemptionStatus;
import com.fptu.exe.skillswap.modules.payment.domain.CouponStatus;
import com.fptu.exe.skillswap.modules.payment.repository.CouponRedemptionRepository;
import com.fptu.exe.skillswap.modules.payment.repository.CouponRepository;
import com.fptu.exe.skillswap.modules.payment.service.CouponService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
public class CouponConcurrency60SlotsTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponRedemptionRepository couponRedemptionRepository;

    @Autowired
    private UserRepository userRepository;

    private Coupon testCoupon;
    private final List<User> createdUsers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        couponRedemptionRepository.deleteAll();
        couponRepository.deleteAll();

        testCoupon = Coupon.builder()
                .code("WELCOME70K")
                .title("Mã chào mừng 70K")
                .description("Giảm 70.000 VNĐ")
                .discountType(CouponDiscountType.FIXED)
                .discountValue(70000)
                .quotaTotal(60)
                .quotaPerUser(1)
                .status(CouponStatus.ACTIVE)
                .build();
        testCoupon = couponRepository.save(testCoupon);

        for (int i = 0; i < 70; i++) {
            User user = User.builder()
                    .fullName("Concurrent Mentee " + i)
                    .email("concurrent.mentee." + i + "." + UUID.randomUUID() + "@test.com")
                    .status(UserStatus.ACTIVE)
                    .roles(Set.of(RoleCode.MENTEE))
                    .build();
            createdUsers.add(userRepository.save(user));
        }
    }

    @AfterEach
    void tearDown() {
        couponRedemptionRepository.deleteAll();
        couponRepository.deleteAll();
        userRepository.deleteAll(createdUsers);
        createdUsers.clear();
    }

    @Test
    void concurrency70Threads_shouldAllowExactly60_andReject10WithConflict() throws InterruptedException {
        int totalRequests = 70;
        int expectedSuccess = 60;
        int expectedConflict = 10;

        List<UUID> orderIds = new ArrayList<>();
        for (int i = 0; i < totalRequests; i++) {
            orderIds.add(UUID.randomUUID());
        }

        ExecutorService executor = Executors.newFixedThreadPool(totalRequests);
        CountDownLatch readyLatch = new CountDownLatch(totalRequests);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalRequests);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        List<Throwable> unexpectedExceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < totalRequests; i++) {
            final UUID userId = createdUsers.get(i).getId();
            final UUID orderId = orderIds.get(i);

            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    couponService.reserveCoupon(testCoupon, orderId, userId, 70000);
                    successCount.incrementAndGet();
                } catch (BaseException e) {
                    if (e.getErrorCode() == ErrorCode.RESOURCE_CONFLICT) {
                        conflictCount.incrementAndGet();
                    } else {
                        unexpectedExceptions.add(e);
                    }
                } catch (Throwable t) {
                    unexpectedExceptions.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(10, TimeUnit.SECONDS);
        startLatch.countDown();
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertTrue(finished, "Tất cả 70 luồng phải hoàn thành trong 30 giây");
        assertTrue(unexpectedExceptions.isEmpty(), "Không được có ngoại lệ bất thường: " + unexpectedExceptions);
        assertEquals(expectedSuccess, successCount.get(), "Chính xác 60 request phải thành công");
        assertEquals(expectedConflict, conflictCount.get(), "Chính xác 10 request phải bị từ chối 409 RESOURCE_CONFLICT");

        long reservedInDb = couponRedemptionRepository.countByCouponIdAndStatusIn(
                testCoupon.getId(),
                List.of(CouponRedemptionStatus.RESERVED)
        );
        assertEquals(60L, reservedInDb, "Số bản ghi RESERVED trong database phải đúng bằng 60");
    }
}
