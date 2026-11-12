package com.fptu.exe.skillswap.modules.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fptu.exe.skillswap.modules.payment.port.CampaignAdminPort;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
@Import(CampaignAdminPortImpl.class)
class CampaignAdminPortImplIntegrationTest {

    @Autowired
    private CampaignAdminPort campaignAdminPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM campaign_benefits");
        jdbcTemplate.execute("DELETE FROM campaign_audience_role_codes");
        jdbcTemplate.execute("DELETE FROM campaign_audience_campus_ids");
        jdbcTemplate.execute("DELETE FROM campaign_audience_program_ids");
        jdbcTemplate.execute("DELETE FROM campaign_audience_specialization_ids");
        jdbcTemplate.execute("DELETE FROM campaigns");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void campaignCrudAndList_serializeOutsideTransactionWithoutLazyInitializationException() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID campusId = UUID.randomUUID();

        CampaignAdminPort.CampaignView created = campaignAdminPort.create(adminId, new CampaignAdminPort.CreateCampaignCommand(
                "Spring Launch",
                "Description",
                "APP_FUNDED",
                null,
                null,
                500000,
                Set.of("MENTEE"),
                Set.of(campusId),
                Set.of(),
                Set.of()
        ));

        PageResponse<CampaignAdminPort.CampaignView> list1 = campaignAdminPort.list(
                new CampaignAdminPort.CampaignListQuery(null, null, null, 0, 20, "createdAt", "DESC")
        );
        String json1 = objectMapper.writeValueAsString(list1);
        assertThat(json1).contains("Spring Launch");
        assertThat(list1.getContent()).hasSize(1);
        assertThat(list1.getContent().getFirst().audienceRoleCodes()).containsExactly("MENTEE");
        assertThat(list1.getContent().getFirst().audienceCampusIds()).containsExactly(campusId);

        PageResponse<CampaignAdminPort.CampaignView> list2 = campaignAdminPort.list(
                new CampaignAdminPort.CampaignListQuery("", " ", "  ", null, null, null, null)
        );
        assertThat(objectMapper.writeValueAsString(list2)).contains("Spring Launch");
        assertThat(list2.getSize()).isEqualTo(20);

        CampaignAdminPort.CampaignView detail = campaignAdminPort.getDetail(created.id());
        assertThat(objectMapper.writeValueAsString(detail)).contains(campusId.toString());

        CampaignAdminPort.CampaignView updated = campaignAdminPort.update(adminId, created.id(),
                new CampaignAdminPort.UpdateCampaignCommand(
                        "Spring Launch Updated", null, null, null, null, 600000, null, null, null, null
                ));
        assertThat(objectMapper.writeValueAsString(updated)).contains("Spring Launch Updated");
        assertThat(updated.audienceRoleCodes()).containsExactly("MENTEE");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void listWithInvalidSortBy_throwsBadRequest() {
        assertThatThrownBy(() -> campaignAdminPort.list(
                new CampaignAdminPort.CampaignListQuery(null, null, null, 0, 20, "badColumn", "DESC")
        )).isInstanceOf(BaseException.class);
    }
}
