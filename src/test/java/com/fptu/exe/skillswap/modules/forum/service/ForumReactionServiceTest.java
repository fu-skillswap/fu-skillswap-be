package com.fptu.exe.skillswap.modules.forum.service;

import com.fptu.exe.skillswap.modules.forum.domain.ForumActionType;
import com.fptu.exe.skillswap.modules.forum.domain.ForumComment;
import com.fptu.exe.skillswap.modules.forum.domain.ForumCommentReaction;
import com.fptu.exe.skillswap.modules.forum.domain.ForumCommentStatus;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPost;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPostReaction;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPostStatus;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReactionType;
import com.fptu.exe.skillswap.modules.forum.domain.ForumTopic;
import com.fptu.exe.skillswap.modules.forum.domain.ForumTopicCode;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumReactionRequest;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumCommentResponse;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumPostResponse;
import com.fptu.exe.skillswap.modules.forum.repository.ForumCommentReactionRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumCommentRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumPostReactionRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumPostRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForumReactionServiceTest {

    @Mock
    private ForumPostRepository forumPostRepository;
    @Mock
    private ForumCommentRepository forumCommentRepository;
    @Mock
    private ForumPostReactionRepository forumPostReactionRepository;
    @Mock
    private ForumCommentReactionRepository forumCommentReactionRepository;
    @Mock
    private ForumActionLogService forumActionLogService;
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private ForumReactionService forumReactionService;

    private User author;
    private User reactingUser;
    private ForumTopic topic;
    private ForumPost post;
    private ForumComment comment;

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        author = User.builder()
                .id(UUID.randomUUID())
                .fullName("Post Author")
                .roles(Set.of(RoleCode.MENTEE))
                .status(UserStatus.ACTIVE)
                .build();

        reactingUser = User.builder()
                .id(UUID.randomUUID())
                .fullName("Reacting User")
                .roles(Set.of(RoleCode.MENTEE))
                .status(UserStatus.ACTIVE)
                .build();

        topic = ForumTopic.builder()
                .id(UUID.randomUUID())
                .code(ForumTopicCode.QUESTION)
                .nameVi("Hỏi đáp")
                .nameEn("Question")
                .displayOrder(1)
                .active(true)
                .build();

        post = ForumPost.builder()
                .id(UUID.randomUUID())
                .authorUser(author)
                .forumTopic(topic)
                .title("Sample Post")
                .content("Sample Content")
                .status(ForumPostStatus.PUBLISHED)
                .reactionCount(0)
                .commentCount(0)
                .reportCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        comment = ForumComment.builder()
                .id(UUID.randomUUID())
                .post(post)
                .authorUser(author)
                .content("Sample Comment")
                .status(ForumCommentStatus.VISIBLE)
                .reactionCount(0)
                .reportCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("upsertPostReaction: First reaction should atomically insert and increment counter")
    void upsertPostReaction_firstTime_shouldIncrementCounter() {
        when(forumPostRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(forumPostReactionRepository.findByPostIdAndUserId(post.getId(), reactingUser.getId()))
                .thenReturn(Optional.empty());
        when(forumPostRepository.getReactionCountById(post.getId())).thenReturn(1);

        ForumPostResponse response = forumReactionService.upsertPostReaction(
                reactingUser, post.getId(), new ForumReactionRequest(ForumReactionType.LIKE)
        );

        verify(forumPostReactionRepository).save(any(ForumPostReaction.class));
        verify(forumPostRepository).incrementReactionCount(post.getId());
        verify(forumActionLogService).record(eq(reactingUser), eq(ForumActionType.TOGGLE_REACTION), eq("POST"), eq(post.getId()), any());
        assertEquals(1, response.reactionCount());
        assertTrue(response.reactedByCurrentUser());
        assertEquals("LIKE", response.myReactionType());
    }

    @Test
    @DisplayName("upsertPostReaction: Duplicate reaction should NOT increment counter (idempotent)")
    void upsertPostReaction_duplicate_shouldNotIncrementCounter() {
        when(forumPostRepository.findById(post.getId())).thenReturn(Optional.of(post));
        ForumPostReaction existing = ForumPostReaction.builder()
                .id(UUID.randomUUID())
                .post(post)
                .user(reactingUser)
                .reactionType(ForumReactionType.LIKE)
                .build();
        when(forumPostReactionRepository.findByPostIdAndUserId(post.getId(), reactingUser.getId()))
                .thenReturn(Optional.of(existing));
        when(forumPostRepository.getReactionCountById(post.getId())).thenReturn(1);

        ForumPostResponse response = forumReactionService.upsertPostReaction(
                reactingUser, post.getId(), new ForumReactionRequest(ForumReactionType.LIKE)
        );

        verify(forumPostReactionRepository, never()).save(any());
        verify(forumPostRepository, never()).incrementReactionCount(any());
        assertEquals(1, response.reactionCount());
        assertTrue(response.reactedByCurrentUser());
    }

    @Test
    @DisplayName("upsertPostReaction: Non-LIKE request should throw BAD_REQUEST")
    void upsertPostReaction_nullOrInvalidType_shouldThrowBadRequest() {
        BaseException ex = assertThrows(BaseException.class, () ->
                forumReactionService.upsertPostReaction(reactingUser, post.getId(), null)
        );
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("upsertPostReaction: Hidden post should throw ResourceNotFoundException")
    void upsertPostReaction_hiddenPost_shouldThrowNotFound() {
        post.setStatus(ForumPostStatus.HIDDEN);
        when(forumPostRepository.findById(post.getId())).thenReturn(Optional.of(post));

        assertThrows(ResourceNotFoundException.class, () ->
                forumReactionService.upsertPostReaction(reactingUser, post.getId(), new ForumReactionRequest(ForumReactionType.LIKE))
        );
        verify(forumPostReactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("removePostReaction: Existing reaction should atomically delete and decrement counter")
    void removePostReaction_existing_shouldDecrementCounter() {
        when(forumPostRepository.findById(post.getId())).thenReturn(Optional.of(post));
        ForumPostReaction existing = ForumPostReaction.builder()
                .id(UUID.randomUUID())
                .post(post)
                .user(reactingUser)
                .reactionType(ForumReactionType.LIKE)
                .build();
        when(forumPostReactionRepository.findByPostIdAndUserId(post.getId(), reactingUser.getId()))
                .thenReturn(Optional.of(existing));
        when(forumPostRepository.getReactionCountById(post.getId())).thenReturn(0);

        ForumPostResponse response = forumReactionService.removePostReaction(reactingUser, post.getId());

        verify(forumPostReactionRepository).delete(existing);
        verify(forumPostRepository).decrementReactionCount(post.getId());
        verify(forumActionLogService).record(eq(reactingUser), eq(ForumActionType.TOGGLE_REACTION), eq("POST"), eq(post.getId()), any());
        assertEquals(0, response.reactionCount());
        assertFalse(response.reactedByCurrentUser());
        assertNull(response.myReactionType());
    }

    @Test
    @DisplayName("removePostReaction: Non-existing reaction should NOT decrement counter")
    void removePostReaction_notReacted_shouldNotDecrementCounter() {
        when(forumPostRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(forumPostReactionRepository.findByPostIdAndUserId(post.getId(), reactingUser.getId()))
                .thenReturn(Optional.empty());
        when(forumPostRepository.getReactionCountById(post.getId())).thenReturn(0);

        ForumPostResponse response = forumReactionService.removePostReaction(reactingUser, post.getId());

        verify(forumPostRepository, never()).decrementReactionCount(any());
        assertEquals(0, response.reactionCount());
        assertFalse(response.reactedByCurrentUser());
    }

    @Test
    @DisplayName("upsertCommentReaction: First reaction should atomically insert and increment counter")
    void upsertCommentReaction_firstTime_shouldIncrementCounter() {
        when(forumCommentRepository.findById(comment.getId())).thenReturn(Optional.of(comment));
        when(forumCommentReactionRepository.findByCommentIdAndUserId(comment.getId(), reactingUser.getId()))
                .thenReturn(Optional.empty());
        when(forumCommentRepository.getReactionCountById(comment.getId())).thenReturn(1);

        ForumCommentResponse response = forumReactionService.upsertCommentReaction(
                reactingUser, comment.getId(), new ForumReactionRequest(ForumReactionType.LIKE)
        );

        verify(forumCommentReactionRepository).save(any(ForumCommentReaction.class));
        verify(forumCommentRepository).incrementReactionCount(comment.getId());
        verify(forumActionLogService).record(eq(reactingUser), eq(ForumActionType.TOGGLE_REACTION), eq("COMMENT"), eq(comment.getId()), any());
        assertEquals(1, response.reactionCount());
        assertTrue(response.reactedByCurrentUser());
    }

    @Test
    @DisplayName("upsertCommentReaction: Non-VISIBLE comment should throw BAD_REQUEST")
    void upsertCommentReaction_hiddenComment_shouldThrowBadRequest() {
        comment.setStatus(ForumCommentStatus.HIDDEN);
        when(forumCommentRepository.findById(comment.getId())).thenReturn(Optional.of(comment));

        BaseException ex = assertThrows(BaseException.class, () ->
                forumReactionService.upsertCommentReaction(reactingUser, comment.getId(), new ForumReactionRequest(ForumReactionType.LIKE))
        );
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        verify(forumCommentReactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("removeCommentReaction: Existing reaction should atomically delete and decrement counter")
    void removeCommentReaction_existing_shouldDecrementCounter() {
        when(forumCommentRepository.findById(comment.getId())).thenReturn(Optional.of(comment));
        ForumCommentReaction existing = ForumCommentReaction.builder()
                .id(UUID.randomUUID())
                .comment(comment)
                .user(reactingUser)
                .reactionType(ForumReactionType.LIKE)
                .build();
        when(forumCommentReactionRepository.findByCommentIdAndUserId(comment.getId(), reactingUser.getId()))
                .thenReturn(Optional.of(existing));
        when(forumCommentRepository.getReactionCountById(comment.getId())).thenReturn(0);

        ForumCommentResponse response = forumReactionService.removeCommentReaction(reactingUser, comment.getId());

        verify(forumCommentReactionRepository).delete(existing);
        verify(forumCommentRepository).decrementReactionCount(comment.getId());
        verify(forumActionLogService).record(eq(reactingUser), eq(ForumActionType.TOGGLE_REACTION), eq("COMMENT"), eq(comment.getId()), any());
        assertEquals(0, response.reactionCount());
        assertFalse(response.reactedByCurrentUser());
    }

    @Test
    @DisplayName("removeCommentReaction: Non-existing reaction should NOT decrement counter")
    void removeCommentReaction_notReacted_shouldNotDecrementCounter() {
        when(forumCommentRepository.findById(comment.getId())).thenReturn(Optional.of(comment));
        when(forumCommentReactionRepository.findByCommentIdAndUserId(comment.getId(), reactingUser.getId()))
                .thenReturn(Optional.empty());
        when(forumCommentRepository.getReactionCountById(comment.getId())).thenReturn(0);

        ForumCommentResponse response = forumReactionService.removeCommentReaction(reactingUser, comment.getId());

        verify(forumCommentRepository, never()).decrementReactionCount(any());
        assertEquals(0, response.reactionCount());
        assertFalse(response.reactedByCurrentUser());
    }
}
