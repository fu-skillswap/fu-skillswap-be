package com.fptu.exe.skillswap.modules.forum.service;

import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.domain.TagStatus;
import com.fptu.exe.skillswap.modules.catalog.domain.TagType;
import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPost;
import com.fptu.exe.skillswap.modules.forum.domain.ForumActionType;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPostStatus;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReport;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReportReasonType;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReportTargetType;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumReportCreateRequest;
import com.fptu.exe.skillswap.modules.forum.repository.ForumCommentRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumPostReactionRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumPostRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumReportRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.shared.cursor.CursorCodec;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForumReportServiceTest {

    @Mock
    private ForumPostRepository forumPostRepository;
    @Mock
    private ForumCommentRepository forumCommentRepository;
    @Mock
    private ForumPostReactionRepository forumPostReactionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ForumReportRepository forumReportRepository;
    @Mock
    private ForumTextPolicy forumTextPolicy;
    @Mock
    private ForumAbuseGuardService forumAbuseGuardService;
    @Mock
    private CursorCodec cursorCodec;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private ForumReportService forumReportService;
    private User reporter;
    private User author;
    private ForumPost post;

    @BeforeEach
    void setUp() {
        ForumPostService forumPostService = new ForumPostService(
                forumPostRepository,
                forumCommentRepository,
                forumPostReactionRepository,
                userRepository,
                tagRepository,
                notificationService,
                forumTextPolicy,
                forumAbuseGuardService,
                cursorCodec,
                eventPublisher
        );
        forumReportService = new ForumReportService(
                forumPostService,
                forumPostRepository,
                forumCommentRepository,
                forumReportRepository,
                forumTextPolicy,
                forumAbuseGuardService
        );

        reporter = User.builder()
                .id(UUID.randomUUID())
                .email("reporter@test.com")
                .fullName("Reporter")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(RoleCode.MENTEE))
                .build();
        author = User.builder()
                .id(UUID.randomUUID())
                .email("author@test.com")
                .fullName("Author")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(RoleCode.MENTEE))
                .build();
        Tag helpTopic = Tag.builder()
                .id(UUID.randomUUID())
                .code("HELP_QA")
                .nameVi("Giải đáp thắc mắc")
                .type(TagType.HELP_TOPIC)
                .status(TagStatus.ACTIVE)
                .build();
        post = ForumPost.builder()
                .id(UUID.randomUUID())
                .authorUser(author)
                .helpTopic(helpTopic)
                .title("Need help")
                .content("content")
                .status(ForumPostStatus.PUBLISHED)
                .reportCount(0)
                .build();
        lenient().when(forumTextPolicy.normalizeOptionalPlainText(any(), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createReport_selfTarget_shouldThrowBadRequest() {
        when(userRepository.findById(author.getId())).thenReturn(Optional.of(author));
        when(forumReportRepository.existsByReporterUserIdAndTargetTypeAndTargetId(author.getId(), ForumReportTargetType.POST, post.getId())).thenReturn(false);
        when(forumPostRepository.findByIdForUpdate(post.getId())).thenReturn(Optional.of(post));

        BaseException ex = assertThrows(BaseException.class, () -> forumReportService.createReport(
                author.getId(),
                new ForumReportCreateRequest(ForumReportTargetType.POST, post.getId(), ForumReportReasonType.SPAM, "spam")
        ));

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void createReport_duplicateReport_shouldThrowConflict() {
        when(userRepository.findById(reporter.getId())).thenReturn(Optional.of(reporter));
        when(forumReportRepository.existsByReporterUserIdAndTargetTypeAndTargetId(reporter.getId(), ForumReportTargetType.POST, post.getId())).thenReturn(true);

        BaseException ex = assertThrows(BaseException.class, () -> forumReportService.createReport(
                reporter.getId(),
                new ForumReportCreateRequest(ForumReportTargetType.POST, post.getId(), ForumReportReasonType.SPAM, "spam")
        ));

        assertEquals(ErrorCode.RESOURCE_CONFLICT, ex.getErrorCode());
    }

    @Test
    void createReport_validPost_shouldIncreaseCount() {
        when(userRepository.findById(reporter.getId())).thenReturn(Optional.of(reporter));
        when(forumReportRepository.existsByReporterUserIdAndTargetTypeAndTargetId(reporter.getId(), ForumReportTargetType.POST, post.getId())).thenReturn(false);
        when(forumPostRepository.findByIdForUpdate(post.getId())).thenReturn(Optional.of(post));
        when(forumPostRepository.save(any(ForumPost.class))).thenAnswer(inv -> inv.getArgument(0));
        when(forumReportRepository.save(any(ForumReport.class))).thenAnswer(inv -> {
            ForumReport report = inv.getArgument(0);
            report.setId(UUID.randomUUID());
            return report;
        });

        var response = forumReportService.createReport(
                reporter.getId(),
                new ForumReportCreateRequest(ForumReportTargetType.POST, post.getId(), ForumReportReasonType.SPAM, "spam")
        );

        assertEquals(1, post.getReportCount());
        assertEquals("OPEN", response.status());
        verify(forumAbuseGuardService).checkAndLog(reporter, ForumActionType.CREATE_REPORT);
        verify(forumPostRepository).save(post);
        verify(forumReportRepository).save(any(ForumReport.class));
    }

    @Test
    void createReport_htmlDescription_shouldThrowBadRequest() {
        when(userRepository.findById(reporter.getId())).thenReturn(Optional.of(reporter));
        when(forumReportRepository.existsByReporterUserIdAndTargetTypeAndTargetId(reporter.getId(), ForumReportTargetType.POST, post.getId())).thenReturn(false);
        when(forumPostRepository.findByIdForUpdate(post.getId())).thenReturn(Optional.of(post));
        when(forumTextPolicy.normalizeOptionalPlainText(eq("<script>x</script>"), eq("Mô tả report")))
                .thenThrow(new BaseException(ErrorCode.BAD_REQUEST, "Mô tả report chỉ hỗ trợ plain text, không chấp nhận HTML"));

        BaseException ex = assertThrows(BaseException.class, () -> forumReportService.createReport(
                reporter.getId(),
                new ForumReportCreateRequest(ForumReportTargetType.POST, post.getId(), ForumReportReasonType.SPAM, "<script>x</script>")
        ));

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }
}
