package com.fptu.exe.skillswap.modules.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.payment.port.CouponAdminPort;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CouponAdminPortImpl.class)
class CouponAdminPortImplIntegrationTest {

    @Autowired
    private CouponAdminPort couponAdminPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private UserQueryPort userQueryPort;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM coupon_redemptions");
        jdbcTemplate.execute("DELETE FROM coupon_applicable_service_ids");
        jdbcTemplate.execute("DELETE FROM coupon_applicable_mentor_ids");
        jdbcTemplate.execute("DELETE FROM coupons");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void couponCrudAndList_serializeOutsideTransactionWithoutLazyInitializationException() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();

        CouponAdminPort.CouponView created = couponAdminPort.create(adminId, new CouponAdminPort.CreateCouponCommand(
                "SAVE10K",
                "Save 10k",
                "Description",
                "FIXED",
                10000,
                null,
                null,
                null,
                100,
                1,
                0,
                Set.of(serviceId),
                Set.of(mentorId)
        ));

        // 1. List with explicit params matching production screenshot
        PageResponse<CouponAdminPort.CouponView> page1 = couponAdminPort.list(
                new CouponAdminPort.CouponListQuery(null, null, null, 0, 20, "createdAt", "DESC")
        );
        String json1 = objectMapper.writeValueAsString(page1);
        assertThat(json1).contains("SAVE10K");
        assertThat(page1.getContent()).hasSize(1);
        assertThat(page1.getContent().getFirst().applicableServiceIds()).containsExactly(serviceId);
        assertThat(page1.getContent().getFirst().applicableMentorIds()).containsExactly(mentorId);

        // 2. List with all nulls / blanks
        PageResponse<CouponAdminPort.CouponView> page2 = couponAdminPort.list(
                new CouponAdminPort.CouponListQuery("", " ", "   ", null, null, null, null)
        );
        assertThat(objectMapper.writeValueAsString(page2)).contains("SAVE10K");
        assertThat(page2.getSize()).isEqualTo(20);

        // 3. Get detail outside transaction
        CouponAdminPort.CouponView detail = couponAdminPort.getDetail(created.id());
        assertThat(objectMapper.writeValueAsString(detail)).contains(serviceId.toString());

        // 4. Update outside transaction
        CouponAdminPort.CouponView updated = couponAdminPort.update(adminId, created.id(),
                new CouponAdminPort.UpdateCouponCommand(
                        null, "Save 15k", null, null, 15000, null,
                        null, null, null, null, null, null, null
                ));
        assertThat(objectMapper.writeValueAsString(updated)).contains("Save 15k");
        assertThat(updated.applicableServiceIds()).containsExactly(serviceId);

        // 5. Change status outside transaction
        CouponAdminPort.CouponView statusChanged = couponAdminPort.changeStatus(adminId, created.id(),
                new CouponAdminPort.ChangeCouponStatusCommand("INACTIVE"));
        assertThat(objectMapper.writeValueAsString(statusChanged)).contains("INACTIVE");
        assertThat(statusChanged.applicableMentorIds()).containsExactly(mentorId);

        // 6. Redemptions with null query params
        PageResponse<CouponAdminPort.CouponRedemptionView> redemptions = couponAdminPort.getRedemptions(
                created.id(), new CouponAdminPort.CouponPageQuery(null, null, null, null)
        );
        assertThat(redemptions.getContent()).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void listWithInvalidSortBy_throwsBadRequestBaseException() {
        assertThatThrownBy(() -> couponAdminPort.list(
                new CouponAdminPort.CouponListQuery(null, null, null, 0, 20, "invalidField", "DESC")
        )).isInstanceOf(BaseException.class);
    }
}
