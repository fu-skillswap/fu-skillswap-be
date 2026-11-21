package com.fptu.exe.skillswap.integration;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceDeliveryMode;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorSubjectResult;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorSubjectResultRepository;
import com.fptu.exe.skillswap.modules.payment.domain.Coupon;
import com.fptu.exe.skillswap.modules.payment.domain.CouponDiscountType;
import com.fptu.exe.skillswap.modules.payment.domain.CouponRedemption;
import com.fptu.exe.skillswap.modules.payment.domain.CouponRedemptionStatus;
import com.fptu.exe.skillswap.modules.payment.domain.CouponStatus;
import com.fptu.exe.skillswap.modules.payment.repository.CouponRedemptionRepository;
import com.fptu.exe.skillswap.modules.payment.repository.CouponRepository;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ServiceCouponPricingPreviewIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MentorProfileRepository mentorProfileRepository;
    @Autowired
    private MentorServiceRepository mentorServiceRepository;
    @Autowired
    private MentorSubjectResultRepository mentorSubjectResultRepository;
    @Autowired
    private CouponRepository couponRepository;
    @Autowired
    private CouponRedemptionRepository couponRedemptionRepository;

    private User mentorUser;
    private User menteeUser;
    private MentorService paidService;
    private Coupon welcomeCoupon;

    @BeforeEach
    void setUp() {
        couponRedemptionRepository.deleteAll();
        welcomeCoupon = couponRepository.findByCode("WELCOME70K").orElseGet(() -> couponRepository.save(
                Coupon.builder()
                        .code("WELCOME70K")
                        .title("Ưu đãi chào mừng thành viên mới (Giới hạn 60 suất)")
                        .description("Giảm 70.000 VNĐ cho 60 tài khoản mentee đầu tiên khi đặt lịch dịch vụ.")
                        .discountType(CouponDiscountType.FIXED)
                        .discountValue(70_000)
                        .minOrderValueScoin(0)
                        .quotaTotal(60)
                        .quotaPerUser(1)
                        .status(CouponStatus.ACTIVE)
                        .build()));

        mentorUser = userRepository.saveAndFlush(User.builder()
                .email("mentor.coupon.preview." + UUID.randomUUID() + "@fpt.edu.vn")
                .fullName("Mentor Coupon Preview")
                .status(UserStatus.ACTIVE)
                .roles(new HashSet<>(Set.of(RoleCode.MENTEE, RoleCode.MENTOR)))
                .build());

        MentorProfile mentorProfile = mentorProfileRepository.saveAndFlush(MentorProfile.builder()
                .userId(mentorUser.getId())
                .status(MentorStatus.ACTIVE)
                .headline("Senior Java Mentor")
                .expertiseDescription("Spring Boot & Architecture Mentoring")
                .phoneNumber("0901234567")
                .foundationSupportLevel(3)
                .outputReviewSupportLevel(3)
                .directionSupportLevel(3)
                .isAvailable(true)
                .sessionDuration(60)
                .teachingMode(TeachingMode.ONLINE)
                .verifiedAt(LocalDateTime.now().minusDays(2))
                .build());

        mentorSubjectResultRepository.saveAndFlush(MentorSubjectResult.builder()
                .mentorProfile(mentorProfile)
                .subjectCode("PRJ301")
                .subjectName("Java Web Application")
                .scoreValue(new BigDecimal("9.20"))
                .displayOrder(1)
                .build());

        paidService = mentorServiceRepository.saveAndFlush(MentorService.builder()
                .mentorProfile(mentorProfile)
                .title("Code Review 1:1")
                .description("Review kiến trúc backend")
                .expectedOutcome("Code chuẩn clean architecture")
                .durationMinutes(60)
                .isFree(false)
                .priceScoin(100_000)
                .isActive(true)
                .deliveryMode(MentorServiceDeliveryMode.ONE_TO_ONE)
                .build());

        menteeUser = userRepository.saveAndFlush(User.builder()
                .email("mentee.coupon.preview." + UUID.randomUUID() + "@fpt.edu.vn")
                .fullName("Mentee Eligible")
                .status(UserStatus.ACTIVE)
                .roles(new HashSet<>(Set.of(RoleCode.MENTEE)))
                .build());
    }

    @Test
    void mentorDetailAndPricingPreview_shouldShowOriginalAndCouponDiscountedPriceWhenQuotaAvailable() throws Exception {
        UserPrincipal principal = UserPrincipal.create(menteeUser.getId(), menteeUser.getEmail(), List.of(RoleCode.MENTEE));

        // 1. Mentee views mentor detail -> sees originalPriceScoin 110000 and discountedPriceScoin 40000
        mockMvc.perform(get("/api/mentors/{mentorUserId}", mentorUser.getId())
                        .with(authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.services[0].priceScoin").value(110_000))
                .andExpect(jsonPath("$.data.services[0].originalPriceScoin").value(110_000))
                .andExpect(jsonPath("$.data.services[0].discountedPriceScoin").value(40_000))
                .andExpect(jsonPath("$.data.services[0].couponDiscountScoin").value(70_000))
                .andExpect(jsonPath("$.data.services[0].hasCouponDiscount").value(true))
                .andExpect(jsonPath("$.data.services[0].couponCode").value("WELCOME70K"))
                .andExpect(jsonPath("$.data.services[0].couponRemainingQuota").value(60));

        // 2. Mentee calls pricing-preview endpoint -> sees consistent coupon breakdown
        mockMvc.perform(get("/api/mentor-services/{serviceId}/pricing-preview", paidService.getId())
                        .with(authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priceScoin").value(110_000))
                .andExpect(jsonPath("$.data.originalPriceScoin").value(110_000))
                .andExpect(jsonPath("$.data.discountedPriceScoin").value(40_000))
                .andExpect(jsonPath("$.data.couponDiscountScoin").value(70_000))
                .andExpect(jsonPath("$.data.hasCouponDiscount").value(true))
                .andExpect(jsonPath("$.data.couponCode").value("WELCOME70K"))
                .andExpect(jsonPath("$.data.couponRemainingQuota").value(60))
                .andExpect(jsonPath("$.data.estimatedPayableScoin").value(40_000));

        // 3. Anonymous guest browsing public mentor detail -> sees promo price too
        mockMvc.perform(get("/api/mentors/{mentorUserId}", mentorUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.services[0].originalPriceScoin").value(110_000))
                .andExpect(jsonPath("$.data.services[0].discountedPriceScoin").value(40_000))
                .andExpect(jsonPath("$.data.services[0].couponCode").value("WELCOME70K"));
    }

    @Test
    void mentorDetailAndPricingPreview_shouldHideDiscountWhenMenteeAlreadyUsedOr60QuotaExhausted() throws Exception {
        UserPrincipal menteePrincipal = UserPrincipal.create(menteeUser.getId(), menteeUser.getEmail(), List.of(RoleCode.MENTEE));

        // Mentee has already redeemed 1/1 WELCOME70K
        couponRedemptionRepository.saveAndFlush(CouponRedemption.builder()
                .couponId(welcomeCoupon.getId())
                .paymentOrderId(UUID.randomUUID())
                .redeemerUserId(menteeUser.getId())
                .status(CouponRedemptionStatus.REDEEMED)
                .discountScoin(70_000)
                .build());

        mockMvc.perform(get("/api/mentors/{mentorUserId}", mentorUser.getId())
                        .with(authentication(new UsernamePasswordAuthenticationToken(menteePrincipal, null, menteePrincipal.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.services[0].originalPriceScoin").value(110_000))
                .andExpect(jsonPath("$.data.services[0].discountedPriceScoin").value(110_000))
                .andExpect(jsonPath("$.data.services[0].couponDiscountScoin").value(0))
                .andExpect(jsonPath("$.data.services[0].hasCouponDiscount").value(false))
                .andExpect(jsonPath("$.data.services[0].couponCode").value(nullValue()));

        // Fill remaining 59 slots -> total 60/60 exhausted
        for (int i = 0; i < 59; i++) {
            couponRedemptionRepository.save(CouponRedemption.builder()
                    .couponId(welcomeCoupon.getId())
                    .paymentOrderId(UUID.randomUUID())
                    .redeemerUserId(UUID.randomUUID())
                    .status(CouponRedemptionStatus.REDEEMED)
                    .discountScoin(70_000)
                    .build());
        }
        couponRedemptionRepository.flush();

        // A brand-new eligible mentee now sees NO coupon discount because 60 slots are exhausted
        User freshMentee = userRepository.saveAndFlush(User.builder()
                .email("fresh.mentee." + UUID.randomUUID() + "@fpt.edu.vn")
                .fullName("Fresh Mentee")
                .status(UserStatus.ACTIVE)
                .roles(new HashSet<>(Set.of(RoleCode.MENTEE)))
                .build());
        UserPrincipal freshPrincipal = UserPrincipal.create(freshMentee.getId(), freshMentee.getEmail(), List.of(RoleCode.MENTEE));

        mockMvc.perform(get("/api/mentor-services/{serviceId}/pricing-preview", paidService.getId())
                        .with(authentication(new UsernamePasswordAuthenticationToken(freshPrincipal, null, freshPrincipal.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalPriceScoin").value(110_000))
                .andExpect(jsonPath("$.data.discountedPriceScoin").value(110_000))
                .andExpect(jsonPath("$.data.couponDiscountScoin").value(0))
                .andExpect(jsonPath("$.data.hasCouponDiscount").value(false))
                .andExpect(jsonPath("$.data.couponCode").value(nullValue()))
                .andExpect(jsonPath("$.data.estimatedPayableScoin").value(110_000));
    }
}
