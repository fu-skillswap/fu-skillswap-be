package com.fptu.exe.skillswap.modules.booking.event;

import com.fptu.exe.skillswap.modules.booking.service.AvailabilityTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class AvailabilityTemplateReconciliationListener {
    private final AvailabilityTemplateService templateService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void reconcileAfterProtectionRelease(AvailabilityTemplateReconciliationRequestedEvent event) {
        try {
            templateService.reconcileImmediately(event.templateId());
        } catch (RuntimeException exception) {
            // The due row was already persisted in the original transaction; scheduler retry remains safe.
            log.warn("Immediate availability template reconciliation failed for {}", event.templateId(), exception);
        }
    }
}
