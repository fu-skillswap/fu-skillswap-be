package com.fptu.exe.skillswap.modules.payment.scheduler;

import com.fptu.exe.skillswap.modules.payment.service.PaymentOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentOrderReconciliationScheduler {

    private final PaymentOrderService paymentOrderService;

    @Scheduled(cron = "0 */10 * * * *", zone = "Asia/Ho_Chi_Minh")
    public void reconcilePendingProviderPayments() {
        try {
            paymentOrderService.reconcileStaleProviderPayments();
        } catch (RuntimeException ex) {
            log.warn("Failed to reconcile stale provider payments: {}", ex.getMessage(), ex);
        }
    }
}
