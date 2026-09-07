package com.fptu.exe.skillswap.modules.forum;

import com.fptu.exe.skillswap.modules.forum.domain.ForumReportReasonType;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReportTargetType;
import com.fptu.exe.skillswap.modules.forum.domain.ForumTopic;
import com.fptu.exe.skillswap.modules.forum.domain.ForumTopicCode;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumCommentUpsertRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumPostUpsertRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumReportCreateRequest;
import com.fptu.exe.skillswap.modules.forum.repository.ForumTopicRepository;
import com.fptu.exe.skillswap.modules.forum.service.ForumPostService;
import com.fptu.exe.skillswap.modules.forum.service.ForumReportService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.notification.NotificationType;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class ForumFlowIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ForumTopicRepository forumTopicRepository;

    @Autowired
    private ForumPostService forumPostService;

    @Autowired
    private ForumReportService forumReportService;

    @Autowired
    private NotificationService notificationService;

    private User author;
    private User commenter;
    private ForumTopic helpTopic;

    @BeforeEach
    void setUp() {
        author = createForumUser("forum-author@test.com", "Forum Author");
        commenter = createForumUser("forum-commenter@test.com", "Forum Commenter");
        helpTopic = forumTopicRepository.findByCodeAndActiveTrue(ForumTopicCode.QUESTION)
                .orElseGet(() -> forumTopicRepository.save(ForumTopic.builder()
                        .id(UUID.randomUUID())
                        .code(ForumTopicCode.QUESTION)
                        .nameVi("Hỏi đáp")
                        .nameEn("Question")
                        .displayOrder(1)
                        .active(true)
                        .build()));
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
    void commentThreading_shouldSeparateRootCommentsAndReplies_withIndependentPaginationAndSecurity() {
        var post = forumPostService.createPost(author.getId(), new ForumPostUpsertRequest(
                "Thảo luận về Spring Boot",
                "Mọi người cùng thảo luận về kiến trúc phân tầng trong Spring Boot.",
                helpTopic.getId(),
                List.of()
        ));

        // 1. Tạo 2 Root comments: A và B
        var rootA = forumPostService.createComment(author.getId(), post.postId(),
                new ForumCommentUpsertRequest("Bình luận gốc A", List.of(), null));
        var rootB = forumPostService.createComment(commenter.getId(), post.postId(),
                new ForumCommentUpsertRequest("Bình luận gốc B", List.of(), null));

        assertNull(rootA.replyToCommentId());
        assertNull(rootB.replyToCommentId());

        // 2. Tạo 3 câu trả lời (replies) cho comment gốc A
        var replyA1 = forumPostService.createComment(commenter.getId(), post.postId(),
                new ForumCommentUpsertRequest("Phản hồi 1 cho A", List.of(), rootA.commentId()));
        var replyA2 = forumPostService.createComment(author.getId(), post.postId(),
                new ForumCommentUpsertRequest("Phản hồi 2 cho A", List.of(), rootA.commentId()));
        var replyA3 = forumPostService.createComment(commenter.getId(), post.postId(),
                new ForumCommentUpsertRequest("Phản hồi 3 cho A", List.of(), rootA.commentId()));

        assertEquals(rootA.commentId(), replyA1.replyToCommentId());
        assertEquals(rootA.commentId(), replyA2.replyToCommentId());
        assertEquals(rootA.commentId(), replyA3.replyToCommentId());

        // 3. Kiểm tra Root comments API: Chỉ trả về rootA và rootB, KHÔNG trả về các reply A1, A2, A3
        var rootCommentsPage = forumPostService.getComments(author.getId(), post.postId(), null, 10);
        assertEquals(2, rootCommentsPage.items().size(), "Chỉ được trả về 2 bình luận gốc");
        assertEquals(rootA.commentId(), rootCommentsPage.items().get(0).commentId());
        assertEquals(rootB.commentId(), rootCommentsPage.items().get(1).commentId());

        // Verify replyCount trên root comments
        assertEquals(3, rootCommentsPage.items().get(0).replyCount(), "Root comment A phải có replyCount = 3");
        assertEquals(0, rootCommentsPage.items().get(1).replyCount(), "Root comment B phải có replyCount = 0");
        assertNull(rootCommentsPage.items().get(0).replyToCommentId());
        assertNull(rootCommentsPage.items().get(1).replyToCommentId());

        // 4. Kiểm tra Replies API với cursor pagination cho Root comment A (page 1, limit 2)
        var repliesPage1 = forumPostService.getCommentReplies(author.getId(), rootA.commentId(), null, 2);
        assertEquals(2, repliesPage1.items().size(), "Trang 1 của replies phải có 2 items");
        assertTrue(repliesPage1.hasNext(), "Trang 1 phải có next page");
        assertNotNull(repliesPage1.nextCursor(), "Next cursor không được null");
        assertEquals(replyA1.commentId(), repliesPage1.items().get(0).commentId());
        assertEquals(replyA2.commentId(), repliesPage1.items().get(1).commentId());
        assertEquals(0, repliesPage1.items().get(0).replyCount(), "Reply con luôn có replyCount = 0");
        assertEquals(0, repliesPage1.items().get(1).replyCount(), "Reply con luôn có replyCount = 0");
        assertEquals(rootA.commentId(), repliesPage1.items().get(0).replyToCommentId());
        assertEquals(author.getFullName(), repliesPage1.items().get(0).replyToUserName());

        // 5. Lấy tiếp trang 2 của replies cho Root comment A (page 2, limit 2)
        var repliesPage2 = forumPostService.getCommentReplies(author.getId(), rootA.commentId(), repliesPage1.nextCursor(), 2);
        assertEquals(1, repliesPage2.items().size(), "Trang 2 chỉ có 1 item (reply A3)");
        assertFalse(repliesPage2.hasNext(), "Trang 2 là trang cuối cùng");
        assertEquals(replyA3.commentId(), repliesPage2.items().get(0).commentId());

        // 6. Root comment B chưa có reply -> Replies API trả về danh sách rỗng
        var repliesB = forumPostService.getCommentReplies(author.getId(), rootB.commentId(), null, 10);
        assertTrue(repliesB.items().isEmpty(), "Root comment B không có phản hồi");
        assertFalse(repliesB.hasNext());

        // 7. Security: Gọi replies với comment ID không tồn tại -> 404 ResourceNotFoundException
        assertThrows(ResourceNotFoundException.class, () ->
                forumPostService.getCommentReplies(author.getId(), UUID.randomUUID(), null, 10)
        );

        // 8. Security: Gọi replies trên một comment vốn dĩ là reply (không phải root) -> 400 BAD_REQUEST
        BaseException badReqEx = assertThrows(BaseException.class, () ->
                forumPostService.getCommentReplies(author.getId(), replyA1.commentId(), null, 10)
        );
        assertEquals(ErrorCode.BAD_REQUEST, badReqEx.getErrorCode());
        assertTrue(badReqEx.getMessage().contains("Bình luận này là phản hồi"));
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
