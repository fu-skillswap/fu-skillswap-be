package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.modules.payment.domain.LedgerAccountType;
import com.fptu.exe.skillswap.modules.payment.repository.CreditLedgerAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CreditLedgerService.class)
class CreditLedgerServiceReadOnlyIntegrationTest {

    @Autowired
    private CreditLedgerService creditLedgerService;

    @Autowired
    private CreditLedgerAccountRepository accountRepository;

    @Test
    @Transactional(readOnly = true)
    void walletQueriesForNewUser_doNotWriteAccountInReadOnlyTransaction() {
        UUID newUserId = UUID.randomUUID();

        assertThat(creditLedgerService.getAvailableBalance(newUserId)).isZero();
        assertThat(creditLedgerService.getRecentTransactions(newUserId)).isEmpty();
        assertThat(accountRepository.findByOwnerTypeAndOwnerId(LedgerAccountType.USER_CREDIT, newUserId)).isEmpty();
    }
}
