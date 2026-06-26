package com.fptu.exe.skillswap.modules.forum.service;

import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.domain.TagStatus;
import com.fptu.exe.skillswap.modules.catalog.domain.TagType;
import com.fptu.exe.skillswap.modules.forum.domain.ForumComment;
import com.fptu.exe.skillswap.modules.forum.domain.ForumCommentStatus;
import com.fptu.exe.skillswap.modules.forum.domain.ForumModerationAction;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPost;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPostStatus;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReport;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReportReasonType;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReportStatus;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReportTargetType;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumReportResolveRequest;
import com.fptu.exe.skillswap.modules.forum.repository.ForumCommentRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumPostReactionRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumPostRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumReportRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminForumModerationServiceTest {

    @Mock
    private ForumPostRepository forumPostRepository;
    @Mock
    private ForumCommentRepository forumCommentRepository;
    @Mock
    private ForumPostReactionRepository forumPostReactionRepository;
    @Mock
    private ForumReportRepository forumReportRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ForumTextPolicy forumTextPolicy;

    private AdminForumModerationService service;
    private ForumPost post;
    private ForumComment comment;
    private ForumReport report;

    @BeforeEach
    void setUp() {
        service = new AdminForumModerationService(
                forumPostRepository,
                forumCommentRepository,
                forumPostReactionRepository,
                forumReportRepository,
                notificationService,
                forumTextPolicy
        );

        User author = User.builder().id(UUID.randomUUID()).fullName("Author").build();
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
                .title("Post")
                .content("Content")
                .status(ForumPostStatus.PUBLISHED)
                .commentCount(1)
                .build();
        comment = ForumComment.builder()
                .id(UUID.randomUUID())
                .post(post)
                .authorUser(author)
                .content("Comment")
                .status(ForumCommentStatus.VISIBLE)
                .build();
        report = ForumReport.builder()
                .id(UUID.randomUUID())
                .reporterUser(User.builder().id(UUID.randomUUID()).fullName("Reporter").build())
                .targetType(ForumReportTargetType.COMMENT)
                .targetId(comment.getId())
                .reasonType(ForumReportReasonType.SPAM)
                .status(ForumReportStatus.OPEN)
                .build();
        when(forumTextPolicy.normalizeOptionalPlainText(any(), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void resolveHideComment_shouldHideCommentAndDecreaseCount() {
        UUID adminId = UUID.randomUUID();
        when(forumReportRepository.findByIdForUpdate(report.getId())).thenReturn(Optional.of(report));
        when(forumCommentRepository.findByIdForUpdate(comment.getId())).thenReturn(Optional.of(comment));
        when(forumPostRepository.findByIdForUpdate(post.getId())).thenReturn(Optional.of(post));
        when(forumCommentRepository.save(any(ForumComment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(forumPostRepository.save(any(ForumPost.class))).thenAnswer(inv -> inv.getArgument(0));
        when(forumReportRepository.save(any(ForumReport.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.resolveReport(adminId, report.getId(), new ForumReportResolveRequest(
                ForumModerationAction.HIDE_COMMENT,
                "Vi phạm"
        ));

        assertEquals("RESOLVED_ACTION_TAKEN", response.status());
        assertEquals(ForumCommentStatus.HIDDEN, comment.getStatus());
        assertEquals(0, post.getCommentCount());
        verify(notificationService).createNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    void resolveConfirmNoAction_shouldKeepContentUntouched() {
        UUID adminId = UUID.randomUUID();
        ForumPost reportedPost = ForumPost.builder()
                .id(UUID.randomUUID())
                .authorUser(User.builder().id(UUID.randomUUID()).fullName("Author").build())
                .helpTopic(post.getHelpTopic())
                .title("Post")
                .content("Content")
                .status(ForumPostStatus.PUBLISHED)
                .build();
        ForumReport postReport = ForumReport.builder()
                .id(UUID.randomUUID())
                .reporterUser(User.builder().id(UUID.randomUUID()).fullName("Reporter").build())
                .targetType(ForumReportTargetType.POST)
                .targetId(reportedPost.getId())
                .reasonType(ForumReportReasonType.OTHER)
                .status(ForumReportStatus.OPEN)
                .build();

        when(forumReportRepository.findByIdForUpdate(postReport.getId())).thenReturn(Optional.of(postReport));
        when(forumReportRepository.save(any(ForumReport.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.resolveReport(adminId, postReport.getId(), new ForumReportResolveRequest(
                ForumModerationAction.CONFIRM_NO_ACTION,
                "Đã kiểm tra"
        ));

        assertEquals("RESOLVED_NO_ACTION", response.status());
        assertEquals(ForumReportStatus.RESOLVED_NO_ACTION, postReport.getStatus());
        verify(notificationService, org.mockito.Mockito.never()).createNotification(any(), any(), any(), any(), any(), any());
    }
}
