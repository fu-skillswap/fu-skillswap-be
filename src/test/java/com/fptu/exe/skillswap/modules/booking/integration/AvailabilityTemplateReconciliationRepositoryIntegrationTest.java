package com.fptu.exe.skillswap.modules.booking.integration;

import com.fptu.exe.skillswap.modules.booking.repository.AvailabilityTemplateReconciliationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AvailabilityTemplateReconciliationRepositoryIntegrationTest {

    @Autowired
    private AvailabilityTemplateReconciliationRepository reconciliationRepository;

    @Test
    void claimQuery_usesPortableLimitBeforeSkipLockedSyntax() {
        LocalDate today = LocalDate.now();

        assertNotNull(reconciliationRepository.findDueTemplateIdsForClaim(
                today,
                today.plusDays(13),
                LocalDateTime.now(),
                50
        ));
    }
}
