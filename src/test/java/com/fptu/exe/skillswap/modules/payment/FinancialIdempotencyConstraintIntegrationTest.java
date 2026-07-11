package com.fptu.exe.skillswap.modules.payment;

import com.fptu.exe.skillswap.modules.payment.domain.CreditOriginType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerAccountType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerSourceType;
import com.fptu.exe.skillswap.modules.payment.domain.SettlementAccount;
import com.fptu.exe.skillswap.modules.payment.domain.SettlementEntry;
import com.fptu.exe.skillswap.modules.payment.domain.SettlementEntryType;
import com.fptu.exe.skillswap.modules.payment.repository.SettlementAccountRepository;
import com.fptu.exe.skillswap.modules.payment.repository.SettlementEntryRepository;
import com.fptu.exe.skillswap.modules.payment.service.CreditLedgerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class FinancialIdempotencyConstraintIntegrationTest {

    @Autowired
    private CreditLedgerService creditLedgerService;

    @Autowired
    private SettlementAccountRepository settlementAccountRepository;

    @Autowired
    private SettlementEntryRepository settlementEntryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpIndexes() {
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uq_credit_ledger_entries_source_entry_origin_test
                ON credit_ledger_entries(account_id, source_type, source_id, entry_type, origin_type)
                """);
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uq_settlement_entries_source_entry_test
                ON settlement_entries(account_id, source_type, source_id, entry_type)
                """);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbcTemplate.execute("TRUNCATE TABLE settlement_entries");
        jdbcTemplate.execute("TRUNCATE TABLE credit_ledger_entries");
        jdbcTemplate.execute("TRUNCATE TABLE settlement_accounts");
        jdbcTemplate.execute("TRUNCATE TABLE credit_ledger_accounts");
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    @Test
    void issueCredit_shouldThrowDataIntegrityViolationException_whenSourceEntryIsDuplicated() {
        UUID userId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();

        creditLedgerService.issueCredit(
                userId,
                CreditOriginType.MANUAL,
                LedgerSourceType.PAYMENT_ORDER,
                sourceId,
                100,
                "first issue"
        );

        assertThrows(DataIntegrityViolationException.class, () -> creditLedgerService.issueCredit(
                userId,
                CreditOriginType.MANUAL,
                LedgerSourceType.PAYMENT_ORDER,
                sourceId,
                100,
                "duplicate issue"
        ));
    }

    @Test
    void settlementRepository_shouldRejectDuplicateSuccessfulReleaseForSameBooking() {
        SettlementAccount mentorSettlementAccount = settlementAccountRepository.saveAndFlush(SettlementAccount.builder()
                .ownerType(LedgerAccountType.MENTOR_SETTLEMENT)
                .ownerId(UUID.randomUUID())
                .accountCode("TEST_SETTLEMENT_" + UUID.randomUUID())
                .build());
        UUID bookingId = UUID.randomUUID();

        settlementEntryRepository.saveAndFlush(SettlementEntry.builder()
                .accountId(mentorSettlementAccount.getId())
                .entryType(SettlementEntryType.RELEASE)
                .sourceType(LedgerSourceType.BOOKING)
                .sourceId(bookingId)
                .amountScoin(500)
                .balanceEffectScoin(500)
                .memo("first release")
                .build());

        assertThrows(DataIntegrityViolationException.class, () -> settlementEntryRepository.saveAndFlush(SettlementEntry.builder()
                .accountId(mentorSettlementAccount.getId())
                .entryType(SettlementEntryType.RELEASE)
                .sourceType(LedgerSourceType.BOOKING)
                .sourceId(bookingId)
                .amountScoin(500)
                .balanceEffectScoin(500)
                .memo("duplicate release")
                .build()));
    }
}
