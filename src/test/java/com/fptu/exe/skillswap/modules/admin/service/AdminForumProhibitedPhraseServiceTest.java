package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.forum.domain.ForumProhibitedPhrase;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumProhibitedPhraseCreateRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumProhibitedPhraseUpdateRequest;
import com.fptu.exe.skillswap.modules.forum.repository.ForumProhibitedPhraseRepository;
import com.fptu.exe.skillswap.modules.forum.service.ForumProhibitedPhrasePolicy;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.shared.cursor.CursorCodec;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.UuidUtil;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import com.fptu.exe.skillswap.modules.forum.event.ForumProhibitedPhraseChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminForumProhibitedPhraseServiceTest {

    @Mock
    private ForumProhibitedPhraseRepository forumProhibitedPhraseRepository;
    @Mock
    private ForumProhibitedPhrasePolicy prohibitedPhrasePolicy;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CursorCodec cursorCodec;
    @Mock
    private AdminAuditWriterService adminAuditWriterService;
    @Mock
    private EntityManager entityManager;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AdminForumProhibitedPhraseService service;
    private User admin;

    @BeforeEach
    void setUp() {
        service = new AdminForumProhibitedPhraseService(
                forumProhibitedPhraseRepository,
                prohibitedPhrasePolicy,
                userRepository,
                cursorCodec,
                adminAuditWriterService,
                entityManager,
                eventPublisher
        );
        admin = User.builder().id(UuidUtil.generateUuidV7()).fullName("Admin").build();
    }

    @Test
    void create_normalizesBeforeCheckingDuplicateAndWritesAudit() {
        when(prohibitedPhrasePolicy.normalizePhrase("Cụm Từ Cấm")).thenReturn("cụm từ cấm");
        when(forumProhibitedPhraseRepository.existsByNormalizedPhrase("cụm từ cấm")).thenReturn(false);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(forumProhibitedPhraseRepository.save(any(ForumProhibitedPhrase.class))).thenAnswer(invocation -> {
            ForumProhibitedPhrase rule = invocation.getArgument(0);
            rule.setId(UuidUtil.generateUuidV7());
            return rule;
        });

        var response = service.create(admin.getId(), new ForumProhibitedPhraseCreateRequest("Cụm Từ Cấm"));

        assertEquals("Cụm Từ Cấm", response.phrase());
        assertEquals(true, response.isActive());
        ArgumentCaptor<ForumProhibitedPhrase> captor = ArgumentCaptor.forClass(ForumProhibitedPhrase.class);
        verify(forumProhibitedPhraseRepository).save(captor.capture());
        assertEquals("cụm từ cấm", captor.getValue().getNormalizedPhrase());
        verify(adminAuditWriterService).writeOperatorEvent(
                eq(admin.getId()), eq("FORUM_PROHIBITED_PHRASE"), eq(response.ruleId()),
                eq("CREATE_FORUM_PROHIBITED_PHRASE"), eq(null), any()
        );
        verify(eventPublisher).publishEvent(any(ForumProhibitedPhraseChangedEvent.class));
    }

    @Test
    void update_rejectsStaleExpectedVersion() {
        ForumProhibitedPhrase rule = ForumProhibitedPhrase.builder()
                .id(UuidUtil.generateUuidV7())
                .phrase("old")
                .normalizedPhrase("old")
                .active(true)
                .version(3)
                .createdByUser(admin)
                .build();
        when(forumProhibitedPhraseRepository.findById(rule.getId())).thenReturn(Optional.of(rule));

        BaseException exception = assertThrows(
                BaseException.class,
                () -> service.update(admin.getId(), rule.getId(), new ForumProhibitedPhraseUpdateRequest("new", 2))
        );

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
    }
}
