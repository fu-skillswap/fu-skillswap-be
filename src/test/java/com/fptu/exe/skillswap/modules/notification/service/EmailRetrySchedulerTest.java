package com.fptu.exe.skillswap.modules.notification.service;

import com.fptu.exe.skillswap.modules.notification.domain.NotificationStatus;
import com.fptu.exe.skillswap.modules.notification.repository.EmailOutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailRetrySchedulerTest {

    @Mock
    private EmailOutboxRepository repository;

    @Mock
    private EmailDispatchService dispatchService;

    @Test
    void schedulerBoundsFailuresAndQuarantinesStaleInFlightSends() {
        when(repository.updateFailedToFatalError(EmailDispatchService.MAX_SEND_ATTEMPTS)).thenReturn(2);
        when(repository.quarantineStaleSending(any())).thenReturn(1);
        when(repository.findBatchByStatusForUpdate(NotificationStatus.PENDING, PageRequest.of(0, 20)))
                .thenReturn(List.of());
        when(repository.findRetryBatchForUpdate(NotificationStatus.FAILED, EmailDispatchService.MAX_SEND_ATTEMPTS,
                PageRequest.of(0, 10))).thenReturn(List.of());

        new EmailRetryScheduler(repository, dispatchService).retryFailedEmails();

        verify(repository).updateFailedToFatalError(EmailDispatchService.MAX_SEND_ATTEMPTS);
        verify(repository).quarantineStaleSending(any());
        verify(repository).findRetryBatchForUpdate(NotificationStatus.FAILED, EmailDispatchService.MAX_SEND_ATTEMPTS,
                PageRequest.of(0, 10));
    }
}
