package com.fptu.exe.skillswap.modules.forum.service;

import com.fptu.exe.skillswap.modules.forum.domain.ForumActionLog;
import com.fptu.exe.skillswap.modules.forum.domain.ForumActionType;
import com.fptu.exe.skillswap.modules.forum.repository.ForumActionLogRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForumActionLogServiceTest {

    @Mock
    private ForumActionLogRepository forumActionLogRepository;
    @Mock
    private UserQueryPort userQueryPort;

    @Test
    void record_persistsActorActionTargetAndSafeMetadata() {
        User actor = User.builder().id(UUID.randomUUID()).build();
        UUID targetId = UUID.randomUUID();
        ForumActionLogService service = new ForumActionLogService(forumActionLogRepository, userQueryPort);

        service.record(actor, ForumActionType.CREATE_POST, "POST", targetId,
                Map.of("status", "PUBLISHED"));

        ArgumentCaptor<ForumActionLog> captor = ArgumentCaptor.forClass(ForumActionLog.class);
        verify(forumActionLogRepository).save(captor.capture());
        ForumActionLog saved = captor.getValue();
        assertEquals(actor, saved.getUser());
        assertEquals(ForumActionType.CREATE_POST, saved.getActionType());
        assertEquals("POST", saved.getTargetType());
        assertEquals(targetId, saved.getTargetId());
        assertTrue(saved.getMetadata().contains("PUBLISHED"));
    }

    @Test
    void recordByActorId_resolvesActorAndPersists() {
        UUID actorId = UUID.randomUUID();
        User actor = User.builder().id(actorId).build();
        when(userQueryPort.findUserById(actorId)).thenReturn(Optional.of(actor));
        ForumActionLogService service = new ForumActionLogService(forumActionLogRepository, userQueryPort);

        service.record(actorId, ForumActionType.ADMIN_DISMISS_REPORT, "REPORT", UUID.randomUUID(), Map.of());

        verify(forumActionLogRepository).save(any(ForumActionLog.class));
    }

    @Test
    void missingActor_isRejectedWithoutWritingAuditLog() {
        UUID actorId = UUID.randomUUID();
        when(userQueryPort.findUserById(actorId)).thenReturn(Optional.empty());
        ForumActionLogService service = new ForumActionLogService(forumActionLogRepository, userQueryPort);

        assertThrows(ResourceNotFoundException.class,
                () -> service.record(actorId, ForumActionType.ADMIN_HIDE_POST, "POST", UUID.randomUUID(), Map.of()));
    }

    @Test
    void records_requireAnExistingBusinessTransaction() throws NoSuchMethodException {
        Method method = ForumActionLogService.class.getDeclaredMethod(
                "record", User.class, ForumActionType.class, String.class, UUID.class, Map.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertEquals(Propagation.MANDATORY, transactional.propagation());
    }
}
