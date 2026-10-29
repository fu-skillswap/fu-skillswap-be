package com.fptu.exe.skillswap.modules.identity.integration;

import com.fptu.exe.skillswap.modules.identity.domain.EmailVerification;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.dto.request.EmailLoginRequest;
import com.fptu.exe.skillswap.modules.identity.dto.request.EmailRegisterRequest;
import com.fptu.exe.skillswap.modules.identity.dto.request.EmailVerifyRequest;
import com.fptu.exe.skillswap.modules.identity.dto.response.EmailRegisterResponse;
import com.fptu.exe.skillswap.modules.identity.dto.response.TokenResponse;
import com.fptu.exe.skillswap.modules.identity.repository.EmailVerificationRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserSessionRepository;
import com.fptu.exe.skillswap.modules.identity.service.EmailAuthService;
import com.fptu.exe.skillswap.modules.payment.domain.Coupon;
import com.fptu.exe.skillswap.modules.payment.domain.CouponDiscountType;
import com.fptu.exe.skillswap.modules.payment.domain.CouponStatus;
import com.fptu.exe.skillswap.modules.payment.dto.response.UserCouponResponse;
import com.fptu.exe.skillswap.modules.payment.repository.CouponRepository;
import com.fptu.exe.skillswap.modules.payment.service.CouponService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class EmailAuthCouponIntegrationTest {

    @Autowired
    private EmailAuthService emailAuthService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private CouponRepository couponRepository;

    private static final String TEST_EMAIL = "integration.mentee@fpt.edu.vn";
    private static final String GOOGLE_EMAIL = "integration.google@fpt.edu.vn";

    @BeforeEach
    void setUp() {
        cleanTestData();
        // Seed WELCOME70K coupon if not already present
        couponRepository.findByCode("WELCOME70K").orElseGet(() -> {
            Coupon coupon = Coupon.builder()
                    .code("WELCOME70K")
                    .title("Mã giảm 70k cho lượt đặt lịch đầu tiên")
                    .description("Giảm tối đa 70.000 S-Coin cho mentee mới khi đặt lịch lần đầu")
                    .discountType(CouponDiscountType.FIXED)
                    .discountValue(70000)
                    .minOrderValueScoin(0)
                    .quotaTotal(60)
                    .quotaPerUser(1)
                    .status(CouponStatus.ACTIVE)
                    .build();
            return couponRepository.save(coupon);
        });
    }

    @AfterEach
    void tearDown() {
        cleanTestData();
    }

    private void cleanTestData() {
        emailVerificationRepository.findAll().stream()
                .filter(v -> v.getEmail().equals(TEST_EMAIL) || v.getEmail().equals(GOOGLE_EMAIL))
                .forEach(emailVerificationRepository::delete);

        userRepository.findByEmail(TEST_EMAIL).ifPresent(u -> {
            userSessionRepository.findAll().stream()
                    .filter(s -> s.getUser().getId().equals(u.getId()))
                    .forEach(userSessionRepository::delete);
            userRepository.delete(u);
        });

        userRepository.findByEmail(GOOGLE_EMAIL).ifPresent(u -> {
            userSessionRepository.findAll().stream()
                    .filter(s -> s.getUser().getId().equals(u.getId()))
                    .forEach(userSessionRepository::delete);
            userRepository.delete(u);
        });
    }

    @Test
    void registerAndVerify_shouldAutoAssignMenteeAndQualifyForWelcomeCoupon() {
        // 1. Register with email
        EmailRegisterResponse regResponse = emailAuthService.register(
                new EmailRegisterRequest(TEST_EMAIL, "SkillSwap@2026", "Mentee Test")
        );
        assertThat(regResponse.isLinked()).isFalse();

        // 2. Query OTP from DB
        EmailVerification verification = emailVerificationRepository
                .findTopByEmailAndVerifiedAtIsNullOrderByCreatedAtDesc(TEST_EMAIL)
                .orElseThrow();
        String otpCode = verification.getOtpCode();
        assertThat(otpCode).hasSize(6);

        // 3. Verify OTP
        TokenResponse tokenResponse = emailAuthService.verifyEmail(
                new EmailVerifyRequest(TEST_EMAIL, otpCode)
        );
        assertThat(tokenResponse.getAccessToken()).isNotBlank();
        assertThat(tokenResponse.getRefreshToken()).isNotBlank();

        // 4. Verify user in DB
        User user = userRepository.findByEmail(TEST_EMAIL).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getRoles()).contains(RoleCode.MENTEE);
        assertThat(user.getPasswordHash()).startsWith("$2a$");

        // 5. Query coupons for this new user
        List<UserCouponResponse> coupons = couponService.getAvailableCouponsForUser(user.getId());
        assertThat(coupons).isNotEmpty();

        UserCouponResponse welcomeCoupon = coupons.stream()
                .filter(c -> "WELCOME70K".equals(c.code()))
                .findFirst()
                .orElseThrow();

        assertThat(welcomeCoupon.isUsable()).isTrue();
        assertThat(welcomeCoupon.quotaTotal()).isEqualTo(60);
        assertThat(welcomeCoupon.remainingQuota()).isGreaterThan(0);
        assertThat(welcomeCoupon.discountValue()).isEqualTo(70000);

        // 6. Login with password
        TokenResponse loginResponse = emailAuthService.login(
                new EmailLoginRequest(TEST_EMAIL, "SkillSwap@2026")
        );
        assertThat(loginResponse.getAccessToken()).isNotBlank();
    }

    @Test
    void registerExistingGoogleUser_shouldLinkAccountAndAllowPasswordLogin() {
        // 1. Create a Google user with null passwordHash
        User googleUser = User.builder()
                .email(GOOGLE_EMAIL)
                .fullName("Google Mentee")
                .status(UserStatus.ACTIVE)
                .roles(new HashSet<>(Set.of(RoleCode.MENTEE)))
                .passwordHash(null)
                .build();
        userRepository.save(googleUser);

        // 2. Register with this email -> triggers linking flow
        EmailRegisterResponse regResponse = emailAuthService.register(
                new EmailRegisterRequest(GOOGLE_EMAIL, "NewPassword@2026", "Google Mentee")
        );
        assertThat(regResponse.isLinked()).isTrue();

        // 3. Query OTP and verify
        EmailVerification verification = emailVerificationRepository
                .findTopByEmailAndVerifiedAtIsNullOrderByCreatedAtDesc(GOOGLE_EMAIL)
                .orElseThrow();
        String otpCode = verification.getOtpCode();

        TokenResponse tokenResponse = emailAuthService.verifyEmail(
                new EmailVerifyRequest(GOOGLE_EMAIL, otpCode)
        );
        assertThat(tokenResponse.getAccessToken()).isNotBlank();

        // 4. User should now have passwordHash set
        User updated = userRepository.findByEmail(GOOGLE_EMAIL).orElseThrow();
        assertThat(updated.getPasswordHash()).startsWith("$2a$");

        // 5. User can now login with password
        TokenResponse loginResponse = emailAuthService.login(
                new EmailLoginRequest(GOOGLE_EMAIL, "NewPassword@2026")
        );
        assertThat(loginResponse.getAccessToken()).isNotBlank();
    }
}
