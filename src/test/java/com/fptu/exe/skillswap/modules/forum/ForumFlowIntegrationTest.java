package com.fptu.exe.skillswap.modules.forum;

import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.domain.TagStatus;
import com.fptu.exe.skillswap.modules.catalog.domain.TagType;
import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReportReasonType;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReportTargetType;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumCommentUpsertRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumPostUpsertRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumReportCreateRequest;
import com.fptu.exe.skillswap.modules.forum.service.ForumPostService;
import com.fptu.exe.skillswap.modules.forum.service.ForumReportService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class ForumFlowIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private ForumPostService forumPostService;

    @Autowired
    private ForumReportService forumReportService;

    @Autowired
    private NotificationService notificationService;

    private User author;
    private User commenter;
    private Tag helpTopic;

    @BeforeEach
    void setUp() {
        author = createForumUser("forum-author@test.com", "Forum Author");
        commenter = createForumUser("forum-commenter@test.com", "Forum Commenter");
        helpTopic = tagRepository.save(Tag.builder()
                .code("FORUM_FLOW_" + UUID.randomUUID())
                .nameVi("Forum Flow Topic")
                .type(TagType.HELP_TOPIC)
                .status(TagStatus.ACTIVE)
                .build());
    }

    @Test
    void commentByAnotherUser_shouldNotifyOwner_butSelfCommentShouldNotCreateSelfNotification() {
        var post = forumPostService.createPost(author.getId(), new ForumPostUpsertRequest(
                "Cần mentor review CV",
                "Mọi người cho em xin feedback về CV backend.",
                helpTopic.getId(),
                List.of()
        ));

        forumPostService.createComment(commenter.getId(), post.postId(), new ForumCommentUpsertRequest("Anh thấy CV ổn, nên bổ sung phần project.", List.of(), null));

        var authorNotifications = notificationService.getMyNotifications(author.getId(), false, PageRequest.of(0, 10));
        assertEquals(1, authorNotifications.getContent().size());
        assertEquals(NotificationType.FORUM_POST_COMMENTED.name(), authorNotifications.getContent().getFirst().getType());
        assertEquals(post.postId(), authorNotifications.getContent().getFirst().getRelatedEntityId());

        var commentsAfterExternalReply = forumPostService.getComments(author.getId(), post.postId(), null, 10);
        assertEquals(1, commentsAfterExternalReply.items().size());
        assertEquals("Anh thấy CV ổn, nên bổ sung phần project.", commentsAfterExternalReply.items().getFirst().content());

        forumPostService.createComment(author.getId(), post.postId(), new ForumCommentUpsertRequest("Cảm ơn anh, em sẽ cập nhật.", List.of(), null));

        var authorNotificationsAfterSelfComment = notificationService.getMyNotifications(author.getId(), false, PageRequest.of(0, 10));
        assertEquals(1, authorNotificationsAfterSelfComment.getContent().size());

        var commentsAfterSelfReply = forumPostService.getComments(author.getId(), post.postId(), null, 10);
        assertEquals(2, commentsAfterSelfReply.items().size());
    }

    @Test
    void reportFlow_shouldPersistOpenReport_andRejectDuplicateReporterTargetPair() {
        var post = forumPostService.createPost(author.getId(), new ForumPostUpsertRequest(
                "Spam bài viết",
                "Nội dung giả lập để test report flow.",
                helpTopic.getId(),
                List.of()
        ));

        var report = forumReportService.createReport(commenter.getId(), new ForumReportCreateRequest(
                ForumReportTargetType.POST,
                post.postId(),
                ForumReportReasonType.SPAM,
                "Bài viết lặp nội dung"
        ));

        assertEquals("OPEN", report.status());
        assertEquals(post.postId(), report.targetId());
        assertEquals(author.getId(), report.targetAuthorUserId());

        var postDetail = forumPostService.getPostDetail(commenter.getId(), post.postId());
        assertEquals(1, postDetail.reportCount());

        BaseException exception = assertThrows(BaseException.class, () -> forumReportService.createReport(
                commenter.getId(),
                new ForumReportCreateRequest(
                        ForumReportTargetType.POST,
                        post.postId(),
                        ForumReportReasonType.SPAM,
                        "Report trùng"
                )
        ));

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
        assertTrue(forumPostService.getPostDetail(commenter.getId(), post.postId()).reportCount() >= 1);
    }

    private User createForumUser(String email, String fullName) {
        return userRepository.save(User.builder()
                .email(email)
                .fullName(fullName)
                .status(UserStatus.ACTIVE)
                .roles(Set.of(RoleCode.MENTEE))
                .build());
    }
}
