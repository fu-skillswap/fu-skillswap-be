package com.fptu.exe.skillswap.modules.forum.integration;

import com.fptu.exe.skillswap.modules.forum.domain.ForumTopic;
import com.fptu.exe.skillswap.modules.forum.domain.ForumTopicCode;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumCommentUpsertRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumPostUpsertRequest;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumCommentResponse;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumPostResponse;
import com.fptu.exe.skillswap.modules.forum.repository.ForumTopicRepository;
import com.fptu.exe.skillswap.modules.forum.service.ForumPostService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrency test verifying that the Forum module does NOT suffer from HikariCP
 * connection pool starvation / deadlock when multiple threads concurrently execute
 * rate-limited write operations (createPost / createComment) under a constrained pool.
 */
@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=3",
        "spring.datasource.hikari.connection-timeout=4000"
})
class ForumConcurrencyPoolStarvationIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ForumTopicRepository forumTopicRepository;

    @Autowired
    private ForumPostService forumPostService;

    private ForumTopic helpTopic;

    @BeforeEach
    void setUp() {
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
    void concurrentCreatePost_underSmallConnectionPool_shouldNotStarveOrDeadlock() throws InterruptedException {
        int threadCount = 10;
        List<User> authors = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            authors.add(createForumUser("pool-starve-author-" + UUID.randomUUID() + "@test.com", "Author " + i));
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    User author = authors.get(index);
                    ForumPostResponse post = forumPostService.createPost(author.getId(), new ForumPostUpsertRequest(
                            "Concurrent Post Title " + index,
                            "Nội dung bài viết đồng thời để test pool starvation " + index,
                            helpTopic.getId(),
                            List.of()
                    ));
                    if (post != null && post.postId() != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finishedInTime = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finishedInTime, "Cả 10 threads phải hoàn thành trong 10s, không được bị treo bởi deadlock / pool starvation");
        assertTrue(errors.isEmpty(), "Không được có exception do ConnectionTimeout: " + errors);
        assertEquals(threadCount, successCount.get(), "Toàn bộ 10 bài viết phải được tạo thành công");
    }

    @Test
    void concurrentCreateComment_underSmallConnectionPool_shouldNotStarveOrDeadlock() throws InterruptedException {
        int threadCount = 10;
        User postAuthor = createForumUser("pool-starve-cmt-owner-" + UUID.randomUUID() + "@test.com", "Post Owner");
        ForumPostResponse post = forumPostService.createPost(postAuthor.getId(), new ForumPostUpsertRequest(
                "Post for Concurrent Commenting " + UUID.randomUUID(),
                "Nội dung bài viết để nhiều người bình luận đồng thời",
                helpTopic.getId(),
                List.of()
        ));

        List<User> commenters = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            commenters.add(createForumUser("pool-starve-commenter-" + UUID.randomUUID() + "@test.com", "Commenter " + i));
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    User commenter = commenters.get(index);
                    ForumCommentResponse comment = forumPostService.createComment(commenter.getId(), post.postId(),
                            new ForumCommentUpsertRequest(
                                    "Bình luận đồng thời số " + index,
                                    List.of(),
                                    null
                            ));
                    if (comment != null && comment.commentId() != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finishedInTime = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finishedInTime, "Cả 10 threads comment phải hoàn thành trong 10s, không bị deadlock");
        assertTrue(errors.isEmpty(), "Không được có exception do ConnectionTimeout: " + errors);
        assertEquals(threadCount, successCount.get(), "Toàn bộ 10 comment phải được tạo thành công");
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
