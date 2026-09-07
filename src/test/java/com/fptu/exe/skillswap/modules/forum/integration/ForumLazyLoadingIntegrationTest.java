package com.fptu.exe.skillswap.modules.forum.integration;

import com.fptu.exe.skillswap.modules.forum.domain.ForumComment;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPost;
import com.fptu.exe.skillswap.modules.forum.domain.ForumTopic;
import com.fptu.exe.skillswap.modules.forum.domain.ForumTopicCode;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumCommentUpsertRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumPostUpsertRequest;
import com.fptu.exe.skillswap.modules.forum.repository.ForumTopicRepository;
import com.fptu.exe.skillswap.modules.forum.service.ForumPostService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class ForumLazyLoadingIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ForumTopicRepository forumTopicRepository;

    @Autowired
    private ForumPostService forumPostService;

    private User author;
    private ForumTopic topic;

    @BeforeEach
    void setUp() {
        author = userRepository.save(User.builder()
                .email("lazy-test-" + UUID.randomUUID() + "@test.com")
                .fullName("Lazy Author")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(RoleCode.MENTEE))
                .build());

        topic = forumTopicRepository.findByCodeAndActiveTrue(ForumTopicCode.QUESTION)
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
    @DisplayName("ForumPost: @ManyToOne associations should be LAZY when loaded without graph")
    void forumPost_manyToOneAssociations_shouldBeLazy() {
        var postResponse = forumPostService.createPost(author.getId(), new ForumPostUpsertRequest(
                "Lazy Post Title", "Lazy Post Content", topic.getId(), List.of()
        ));

        // Flush and clear persistence context to force a clean select
        entityManager.flush();
        entityManager.clear();

        // Load entity directly without entity graph
        ForumPost loadedPost = entityManager.find(ForumPost.class, postResponse.postId());
        assertNotNull(loadedPost);

        // Verify that authorUser and forumTopic are uninitialized proxies
        assertFalse(Hibernate.isInitialized(loadedPost.getAuthorUser()),
                "authorUser must not be eagerly loaded by default");
        assertFalse(Hibernate.isInitialized(loadedPost.getForumTopic()),
                "forumTopic must not be eagerly loaded by default");
    }

    @Test
    @DisplayName("ForumComment: @ManyToOne associations should be LAZY when loaded without graph")
    void forumComment_manyToOneAssociations_shouldBeLazy() {
        var postResponse = forumPostService.createPost(author.getId(), new ForumPostUpsertRequest(
                "Post for comment", "Content", topic.getId(), List.of()
        ));
        var commentResponse = forumPostService.createComment(author.getId(), postResponse.postId(),
                new ForumCommentUpsertRequest("Comment content", List.of(), null));

        entityManager.flush();
        entityManager.clear();

        ForumComment loadedComment = entityManager.find(ForumComment.class, commentResponse.commentId());
        assertNotNull(loadedComment);

        assertFalse(Hibernate.isInitialized(loadedComment.getAuthorUser()),
                "authorUser in ForumComment must be lazy by default");
        assertFalse(Hibernate.isInitialized(loadedComment.getPost()),
                "post in ForumComment must be lazy by default");
    }

    @Test
    @DisplayName("API retrieval: getPostById and getComments must successfully map DTOs without LazyInitializationException")
    void apiRetrieval_shouldSuccessfullyMapAllFields() {
        var postResponse = forumPostService.createPost(author.getId(), new ForumPostUpsertRequest(
                "Full retrieval post", "Content", topic.getId(), List.of()
        ));
        forumPostService.createComment(author.getId(), postResponse.postId(),
                new ForumCommentUpsertRequest("Visible comment", List.of(), null));

        entityManager.flush();
        entityManager.clear();

        // Retrieve post through service
        var postDto = forumPostService.getPostDetail(author.getId(), postResponse.postId());
        assertNotNull(postDto);
        assertEquals(author.getId(), postDto.authorUserId());
        assertEquals("Lazy Author", postDto.authorFullName());
        assertEquals(topic.getId(), postDto.forumTopic().id());

        // Retrieve comments through service
        var commentsDto = forumPostService.getComments(author.getId(), postResponse.postId(), null, 10);
        assertNotNull(commentsDto);
        assertEquals(1, commentsDto.items().size());
        assertEquals("Visible comment", commentsDto.items().getFirst().content());
        assertEquals(author.getId(), commentsDto.items().getFirst().authorUserId());
    }
}
