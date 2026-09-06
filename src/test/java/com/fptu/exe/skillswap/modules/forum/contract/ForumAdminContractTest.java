package com.fptu.exe.skillswap.modules.forum.contract;

import com.fptu.exe.skillswap.modules.forum.domain.ForumCommentStatus;
import com.fptu.exe.skillswap.modules.forum.domain.ForumModerationAction;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPostStatus;
import com.fptu.exe.skillswap.modules.forum.domain.ForumProhibitedPhrase;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReportStatus;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumReportResolveRequest;
import com.fptu.exe.skillswap.modules.forum.port.CreateForumProhibitedPhraseCommand;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.ReportListQuery;
import com.fptu.exe.skillswap.modules.forum.port.SetForumProhibitedPhraseActiveCommand;
import com.fptu.exe.skillswap.modules.forum.port.UpdateForumProhibitedPhraseCommand;
import com.fptu.exe.skillswap.modules.forum.repository.ForumCommentRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumPostReactionRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumPostRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumProhibitedPhraseRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumReportRepository;
import com.fptu.exe.skillswap.modules.forum.service.ForumAdminPortImpl;
import com.fptu.exe.skillswap.modules.forum.service.ForumActionLogService;
import com.fptu.exe.skillswap.modules.forum.service.ForumProhibitedPhraseAdminPortImpl;
import com.fptu.exe.skillswap.modules.forum.service.ForumProhibitedPhrasePolicy;
import com.fptu.exe.skillswap.modules.forum.service.ForumTextPolicy;
import com.fptu.exe.skillswap.modules.notification.port.NotificationCommandPort;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.shared.cursor.CursorCodec;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ForumAdminContractTest {

    @Mock
    private ForumPostRepository forumPostRepository;
    @Mock
    private ForumCommentRepository forumCommentRepository;
    @Mock
    private ForumPostReactionRepository forumPostReactionRepository;
    @Mock
    private ForumReportRepository forumReportRepository;
    @Mock
    private NotificationCommandPort notificationCommandPort;
    @Mock
    private ForumTextPolicy forumTextPolicy;
    @Mock
    private ForumActionLogService forumActionLogService;
    @Mock
    private CursorCodec cursorCodec;
    @Mock
    private ForumProhibitedPhraseRepository forumProhibitedPhraseRepository;
    @Mock
    private ForumProhibitedPhrasePolicy prohibitedPhrasePolicy;
    @Mock
    private EntityManager entityManager;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private UserQueryPort userQueryPort;

    private ValidatorFactory validatorFactory;
    private Validator validator;
    private ForumAdminPortImpl forumAdminPort;

    @BeforeAll
    void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @BeforeEach
    void setUp() {
        forumAdminPort = new ForumAdminPortImpl(
                forumPostRepository,
                forumCommentRepository,
                forumPostReactionRepository,
                forumReportRepository,
                notificationCommandPort,
                forumTextPolicy,
                cursorCodec,
                forumActionLogService
        );
    }

    @AfterAll
    void closeValidatorFactory() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    @Test
    void reportStatusContract_matchesBackendEnum() {
        assertEquals(
                List.of("OPEN", "RESOLVED_NO_ACTION", "RESOLVED_ACTION_TAKEN", "DISMISSED"),
                forumAdminPort.reportStatusNames()
        );
        assertEquals(List.of("PUBLISHED", "HIDDEN"), List.of(ForumPostStatus.values()).stream().map(Enum::name).toList());
        assertEquals(List.of("VISIBLE", "HIDDEN"), List.of(ForumCommentStatus.values()).stream().map(Enum::name).toList());
    }

    @Test
    void validReportStatuses_areAcceptedByAdminQuery() {
        when(forumReportRepository.searchReports(any(), any(), any(), any())).thenReturn(Page.empty());

        for (ForumReportStatus status : ForumReportStatus.values()) {
            assertDoesNotThrow(() -> forumAdminPort.getReports(new ReportListQuery(0, 20, null, status.name(), null)));
        }
    }

    @Test
    void invalidReportStatus_isRejectedWithBadRequest() {
        BaseException exception = assertThrows(BaseException.class,
                () -> forumAdminPort.getReports(new ReportListQuery(0, 20, null, "PENDING", null)));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    void validModerationActions_areAcceptedByResolveContract() {
        UUID reportId = UUID.randomUUID();
        when(forumReportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.empty());

        for (ForumModerationAction action : ForumModerationAction.values()) {
            assertThrows(ResourceNotFoundException.class,
                    () -> forumAdminPort.resolveReport(UUID.randomUUID(), reportId,
                            new com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.ResolveReportCommand(action.name(), "review")));
            assertTrue(validator.validate(new ForumReportResolveRequest(action, "review")).isEmpty());
        }
    }

    @Test
    void invalidModerationAction_isRejectedWithBadRequest() {
        BaseException exception = assertThrows(BaseException.class,
                () -> forumAdminPort.resolveReport(UUID.randomUUID(), UUID.randomUUID(),
                        new com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.ResolveReportCommand(
                                "HIDE_CONTENT", "review")));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    void prohibitedPhraseCommands_matchValidationContract() {
        assertFalse(validator.validate(new CreateForumProhibitedPhraseCommand("valid phrase")).stream().findAny().isPresent());
        assertTrue(hasViolation(validator.validate(new CreateForumProhibitedPhraseCommand("")), "phrase"));
        assertTrue(hasViolation(validator.validate(new CreateForumProhibitedPhraseCommand("x".repeat(201))), "phrase"));
        assertTrue(hasViolation(validator.validate(new UpdateForumProhibitedPhraseCommand("phrase", null)), "expectedVersion"));
        assertTrue(hasViolation(validator.validate(new SetForumProhibitedPhraseActiveCommand(true, null)), "expectedVersion"));
        assertTrue(hasViolation(validator.validate(new SetForumProhibitedPhraseActiveCommand(null, 0)), "isActive"));
    }

    @Test
    void staleProhibitedPhraseVersion_isRejectedWithConflict() {
        UUID ruleId = UUID.randomUUID();
        ForumProhibitedPhrase rule = ForumProhibitedPhrase.builder()
                .id(ruleId)
                .phrase("old phrase")
                .normalizedPhrase("old phrase")
                .version(3)
                .build();
        when(forumProhibitedPhraseRepository.findById(ruleId)).thenReturn(Optional.of(rule));

        ForumProhibitedPhraseAdminPortImpl service = new ForumProhibitedPhraseAdminPortImpl(
                forumProhibitedPhraseRepository,
                prohibitedPhrasePolicy,
                cursorCodec,
                entityManager,
                eventPublisher,
                userQueryPort
        );

        BaseException exception = assertThrows(BaseException.class,
                () -> service.update(UUID.randomUUID(), ruleId,
                        new UpdateForumProhibitedPhraseCommand("new phrase", 2)));

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
    }

    private boolean hasViolation(Set<? extends jakarta.validation.ConstraintViolation<?>> violations, String property) {
        return violations.stream().anyMatch(violation -> violation.getPropertyPath().toString().equals(property));
    }
}
