package com.fptu.exe.skillswap.modules.booking;

import com.fptu.exe.skillswap.modules.booking.domain.GroupSession;
import com.fptu.exe.skillswap.modules.booking.domain.GroupSessionRegistrationStatus;
import com.fptu.exe.skillswap.modules.booking.domain.GroupSessionStatus;
import com.fptu.exe.skillswap.modules.booking.repository.GroupSessionRepository;
import com.fptu.exe.skillswap.modules.booking.service.GroupSessionLifecycleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupSessionLifecycleServiceTest {

    @Mock
    private GroupSessionRepository groupSessionRepository;

    @Test
    void processDueSessions_closesRegistrationAndStartsOpenSessionIdempotently() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        GroupSession session = GroupSession.builder()
                .id(id)
                .status(GroupSessionStatus.OPEN)
                .registrationStatus(GroupSessionRegistrationStatus.OPEN)
                .registrationClosesAt(now.minusMinutes(2))
                .scheduledStartAt(now.minusMinutes(1))
                .scheduledEndAt(now.plusMinutes(59))
                .build();
        when(groupSessionRepository.findLifecycleCandidates(any(), any(), any(), any(), any()))
                .thenReturn(List.of(id));
        when(groupSessionRepository.findById(id)).thenReturn(Optional.of(session));

        GroupSessionLifecycleService service = new GroupSessionLifecycleService(groupSessionRepository);

        assertEquals(1, service.processDueSessions());
        assertEquals(GroupSessionRegistrationStatus.CLOSED, session.getRegistrationStatus());
        assertEquals(GroupSessionStatus.IN_PROGRESS, session.getStatus());
        assertEquals(0, service.processDueSessions());
    }
}
