package com.fptu.exe.skillswap.modules.forum;

import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.domain.TagStatus;
import com.fptu.exe.skillswap.modules.catalog.domain.TagType;
import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReactionType;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumCommentUpsertRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumPostUpsertRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumReactionRequest;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumCommentResponse;
import com.fptu.exe.skillswap.modules.forum.service.ForumPostService;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Transactional
class ForumPhase5IntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private ForumPostService forumPostService;

    @Autowired
    private NotificationService notificationService;

    private User postAuthor;
    private User commenter;
    private User replier;
    private Tag helpTopic;

    @BeforeEach
    void setUp() {
        String uniqueSuffix = UUID.randomUUID().toString();
        postAuthor = createForumUser("post-author-" + uniqueSuffix + "@test.com", "Post Author");
        commenter = createForumUser("commenter-" + uniqueSuffix + "@test.com", "Commenter User");
        replier = createForumUser("replier-" + uniqueSuffix + "@test.com", "Replier User");
        helpTopic = tagRepository.save(Tag.builder()
                .code("FORUM_P5_" + uniqueSuffix)
                .nameVi("Forum Phase 5 Topic")
                .type(TagType.HELP_TOPIC)
                .status(TagStatus.ACTIVE)
                .build());
    }

    @Test
    void commentReaction_shouldBeIdempotentAndEnforceConstraints() {
        var post = forumPostService.createPost(postAuthor.getId(), new ForumPostUpsertRequest(
                "Test Reaction Post", "Content", helpTopic.getId(), List.of()
        ));
        var comment = forumPostService.createComment(commenter.getId(), post.postId(), new ForumCommentUpsertRequest("Comment 1", List.of(), null));

        // 1. PUT reaction lần đầu tăng count
        var c1 = forumPostService.upsertCommentReaction(replier.getId(), comment.commentId(), new ForumReactionRequest(ForumReactionType.LIKE));
        assertEquals(1, c1.reactionCount());
        assertTrue(c1.reactedByCurrentUser());

        // 2. PUT reaction lần hai không tăng count (Idempotent)
        var c2 = forumPostService.upsertCommentReaction(replier.getId(), comment.commentId(), new ForumReactionRequest(ForumReactionType.LIKE));
        assertEquals(1, c2.reactionCount());

        // 3. DELETE reaction giảm count
        var c3 = forumPostService.removeCommentReaction(replier.getId(), comment.commentId());
        assertEquals(0, c3.reactionCount());
        assertFalse(c3.reactedByCurrentUser());

        // 4. DELETE reaction khi chưa react không lỗi (Idempotent)
        var c4 = forumPostService.removeCommentReaction(replier.getId(), comment.commentId());
        assertEquals(0, c4.reactionCount());

        // 5. Thử react lên comment đã bị xóa
        forumPostService.deleteComment(commenter.getId(), comment.commentId());
        BaseException ex = assertThrows(BaseException.class, () -> 
                forumPostService.upsertCommentReaction(replier.getId(), comment.commentId(), new ForumReactionRequest(ForumReactionType.LIKE))
        );
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void oneLevelReply_shouldEnforceConstraintsAndNotifyProperly() {
        var post = forumPostService.createPost(postAuthor.getId(), new ForumPostUpsertRequest(
                "Test Reply Post", "Content", helpTopic.getId(), List.of()
        ));
        var post2 = forumPostService.createPost(postAuthor.getId(), new ForumPostUpsertRequest(
                "Another Post", "Content", helpTopic.getId(), List.of()
        ));

        // Create base comment
        var baseComment = forumPostService.createComment(commenter.getId(), post.postId(), new ForumCommentUpsertRequest("Base comment", List.of(), null));

        // 1. Reply vào comment cùng post OK
        var reply = forumPostService.createComment(replier.getId(), post.postId(), new ForumCommentUpsertRequest("Reply 1", List.of(), baseComment.commentId()));
        assertEquals(baseComment.commentId(), reply.replyToCommentId());
        assertEquals(commenter.getId(), reply.replyToUserId());
        assertEquals("Commenter User", reply.replyToUserName());

        // 2. Reply vào comment post khác bị chặn
        BaseException ex1 = assertThrows(BaseException.class, () -> 
                forumPostService.createComment(replier.getId(), post2.postId(), new ForumCommentUpsertRequest("Reply bad", List.of(), baseComment.commentId()))
        );
        assertEquals(ErrorCode.BAD_REQUEST, ex1.getErrorCode());

        // 3. Reply vào reply bị chặn (Strict 1-level)
        BaseException ex2 = assertThrows(BaseException.class, () -> 
                forumPostService.createComment(postAuthor.getId(), post.postId(), new ForumCommentUpsertRequest("Nested reply", List.of(), reply.commentId()))
        );
        assertEquals(ErrorCode.BAD_REQUEST, ex2.getErrorCode());

        // 4. Notification logic check
        // Replier trả lời Commenter -> Commenter nhận được FORUM_COMMENT_REPLY
        var commenterNotifs = notificationService.getMyNotifications(commenter.getId(), false, PageRequest.of(0, 10));
        assertEquals(1, commenterNotifs.getContent().size());
        assertEquals(NotificationType.FORUM_COMMENT_REPLY.name(), commenterNotifs.getContent().getFirst().getType());
        
        // Self-reply không gửi notification
        var selfReply = forumPostService.createComment(commenter.getId(), post.postId(), new ForumCommentUpsertRequest("Self reply", List.of(), baseComment.commentId()));
        var commenterNotifsAfterSelf = notificationService.getMyNotifications(commenter.getId(), false, PageRequest.of(0, 10));
        assertEquals(1, commenterNotifsAfterSelf.getContent().size()); // Vẫn là 1

        // Reply chủ post chỉ nhận 1 notification (FORUM_COMMENT_REPLY, ko bị FORUM_POST_COMMENTED đè lên)
        var postOwnerComment = forumPostService.createComment(postAuthor.getId(), post.postId(), new ForumCommentUpsertRequest("Owner comment", List.of(), null));
        var replierToOwner = forumPostService.createComment(replier.getId(), post.postId(), new ForumCommentUpsertRequest("Reply owner", List.of(), postOwnerComment.commentId()));
        
        // Let's count postAuthor notifications
        var postAuthorNotifs = notificationService.getMyNotifications(postAuthor.getId(), false, PageRequest.of(0, 10));
        // postAuthor should have: 
        // 1 from baseComment (FORUM_POST_COMMENTED)
        // 1 from reply 1 (NONE, because we skip post owner if it's a reply)
        // 1 from selfReply (NONE)
        // 1 from replierToOwner (FORUM_COMMENT_REPLY)
        assertEquals(2, postAuthorNotifs.getContent().size());
        assertTrue(postAuthorNotifs.getContent().stream().anyMatch(n -> n.getType().equals(NotificationType.FORUM_COMMENT_REPLY.name())));
    }

    @Test
    void concurrentReaction_shouldNotCauseRaceCondition() throws InterruptedException {
        var post = forumPostService.createPost(postAuthor.getId(), new ForumPostUpsertRequest(
                "Concurrent Post", "Content", helpTopic.getId(), List.of()
        ));
        var comment = forumPostService.createComment(commenter.getId(), post.postId(), new ForumCommentUpsertRequest("Base comment", List.of(), null));

        // Commit the transaction so worker threads can see the post and comment
        org.springframework.test.context.transaction.TestTransaction.flagForCommit();
        org.springframework.test.context.transaction.TestTransaction.end();

        int threadCount = 10;
        java.util.concurrent.ExecutorService executorService = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    forumPostService.upsertCommentReaction(replier.getId(), comment.commentId(), new ForumReactionRequest(ForumReactionType.LIKE));
                } catch (Exception e) {
                    // Concurrent duplicate requests may hit the unique constraint; the final count is asserted below.
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(completed, "Threads should finish within timeout");
        executorService.shutdown();

        // Query again
        var comments = forumPostService.getComments(postAuthor.getId(), post.postId(), null, 10);
        assertEquals(1, comments.items().getFirst().reactionCount(), "Reaction count must be exactly 1 despite concurrent requests");
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
