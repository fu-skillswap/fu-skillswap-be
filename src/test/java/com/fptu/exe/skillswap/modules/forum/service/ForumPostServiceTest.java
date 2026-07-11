package com.fptu.exe.skillswap.modules.forum.service;

import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.domain.TagStatus;
import com.fptu.exe.skillswap.modules.catalog.domain.TagType;
import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
import com.fptu.exe.skillswap.modules.forum.domain.ForumComment;
import com.fptu.exe.skillswap.modules.forum.domain.ForumActionType;
import com.fptu.exe.skillswap.modules.forum.domain.ForumCommentStatus;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPost;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPostReaction;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPostStatus;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReactionType;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumCommentUpsertRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumPostUpsertRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumReactionRequest;
import com.fptu.exe.skillswap.modules.forum.repository.ForumCommentRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumCommentReactionRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumPostReactionRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumPostRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.shared.cursor.CursorCodec;
import com.fptu.exe.skillswap.shared.cursor.CursorTokenPayload;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.UuidUtil;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForumPostServiceTest {

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
    private ForumTextPolicy forumTextPolicy;
    @Mock
    private ForumAbuseGuardService forumAbuseGuardService;
    @Mock
    private CursorCodec cursorCodec;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock
    private ForumCommentReactionRepository forumCommentReactionRepository;

    private ForumPostService forumPostService;
    private User mentee;
    private Tag helpTopic;

    @BeforeEach
    void setUp() {
        forumPostService = new ForumPostService(
                forumPostRepository,
                forumCommentRepository,
                forumPostReactionRepository,
                forumCommentReactionRepository,
                userRepository,
                tagRepository,
                notificationService,
                forumTextPolicy,
                forumAbuseGuardService,
                cursorCodec,
                eventPublisher
        );

        mentee = User.builder()
                .id(UuidUtil.generateUuidV7())
                .email("mentee@test.com")
                .fullName("Nguyen Van A")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(RoleCode.MENTEE))
                .build();

        helpTopic = Tag.builder()
                .id(UuidUtil.generateUuidV7())
                .code("HELP_QA")
                .nameVi("Giải đáp thắc mắc")
                .type(TagType.HELP_TOPIC)
                .status(TagStatus.ACTIVE)
                .build();

        lenient().when(forumTextPolicy.requirePlainText(any(), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createPost_validRequest_shouldPersist() {
        when(userRepository.findById(mentee.getId())).thenReturn(Optional.of(mentee));
        when(tagRepository.findById(helpTopic.getId())).thenReturn(Optional.of(helpTopic));
        when(forumPostRepository.save(any(ForumPost.class))).thenAnswer(inv -> {
            ForumPost post = inv.getArgument(0);
            post.setId(UuidUtil.generateUuidV7());
            return post;
        });

        var response = forumPostService.createPost(mentee.getId(), new ForumPostUpsertRequest(
                "Cần hỏi về PRJ301",
                "Mọi người cho em xin tips làm API.",
                helpTopic.getId(),
                List.of()
        ));

        assertEquals("Cần hỏi về PRJ301", response.title());
        assertEquals("PUBLISHED", response.status());
        verify(forumAbuseGuardService).checkAndLog(mentee, ForumActionType.CREATE_POST);
        verify(forumPostRepository).save(any(ForumPost.class));
    }

    @Test
    void getPosts_firstWindow_shouldReturnCursorPage() {
        ForumPost firstPost = ForumPost.builder()
                .id(UuidUtil.generateUuidV7())
                .authorUser(mentee)
                .helpTopic(helpTopic)
                .title("First")
                .content("First content")
                .status(ForumPostStatus.PUBLISHED)
                .lastActivityAt(LocalDateTime.of(2026, 7, 8, 9, 0))
                .build();
        ForumPost secondPost = ForumPost.builder()
                .id(UuidUtil.generateUuidV7())
                .authorUser(mentee)
                .helpTopic(helpTopic)
                .title("Second")
                .content("Second content")
                .status(ForumPostStatus.PUBLISHED)
                .lastActivityAt(LocalDateTime.of(2026, 7, 8, 8, 0))
                .build();
        ForumPost overflowPost = ForumPost.builder()
                .id(UuidUtil.generateUuidV7())
                .authorUser(mentee)
                .helpTopic(helpTopic)
                .title("Overflow")
                .content("Overflow content")
                .status(ForumPostStatus.PUBLISHED)
                .lastActivityAt(LocalDateTime.of(2026, 7, 8, 7, 0))
                .build();

        when(userRepository.findById(mentee.getId())).thenReturn(Optional.of(mentee));
        when(forumPostRepository.findWindow(any(), eq(3))).thenReturn(List.of(firstPost, secondPost, overflowPost));
        when(forumPostReactionRepository.findReactedPostIdsByUserIdAndPostIdIn(eq(mentee.getId()), any()))
                .thenReturn(List.of(firstPost.getId()));
        when(cursorCodec.encode(any(CursorTokenPayload.class))).thenReturn("next-cursor");

        CursorPageResponse<?> response = forumPostService.getPosts(mentee.getId(), null, 2, null, null, false);

        assertEquals(2, response.items().size());
        assertTrue(response.hasNext());
        assertFalse(response.hasPrev());
        assertEquals("next-cursor", response.nextCursor());
    }

    @Test
    void getPosts_filterMismatchCursor_shouldThrowBadRequest() {
        when(userRepository.findById(mentee.getId())).thenReturn(Optional.of(mentee));
        when(cursorCodec.decode("bad-filter-cursor")).thenReturn(CursorTokenPayload.builder()
                .sortKey("2026-07-08T09:00:00")
                .secondaryKey(UuidUtil.generateUuidV7().toString())
                .direction("NEXT")
                .filterHash("forum-posts:user|keyword=java|helpTopicId=_|mine=false|status=PUBLISHED")
                .build());

        BaseException ex = assertThrows(BaseException.class, () -> forumPostService.getPosts(
                mentee.getId(),
                "bad-filter-cursor",
                20,
                null,
                null,
                false
        ));

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void updatePost_notOwner_shouldThrowForbidden() {
        User other = User.builder()
                .id(UuidUtil.generateUuidV7())
                .email("other@test.com")
                .fullName("Other User")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(RoleCode.MENTEE))
                .build();
        ForumPost post = ForumPost.builder()
                .id(UuidUtil.generateUuidV7())
                .authorUser(other)
                .helpTopic(helpTopic)
                .title("Old")
                .content("Old content")
                .status(ForumPostStatus.PUBLISHED)
                .build();

        when(userRepository.findById(mentee.getId())).thenReturn(Optional.of(mentee));
        when(forumPostRepository.findByIdForUpdate(post.getId())).thenReturn(Optional.of(post));

        BaseException ex = assertThrows(BaseException.class, () -> forumPostService.updatePost(
                mentee.getId(),
                post.getId(),
                new ForumPostUpsertRequest("New", "New content", helpTopic.getId(), List.of())
        ));

        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    void createComment_hiddenPost_shouldThrowNotFound() {
        ForumPost post = ForumPost.builder()
                .id(UuidUtil.generateUuidV7())
                .authorUser(mentee)
                .helpTopic(helpTopic)
                .title("Hidden post")
                .content("Hidden")
                .status(ForumPostStatus.HIDDEN)
                .build();

        when(userRepository.findById(mentee.getId())).thenReturn(Optional.of(mentee));
        when(forumPostRepository.findByIdForUpdate(post.getId())).thenReturn(Optional.of(post));

        BaseException ex = assertThrows(BaseException.class, () -> forumPostService.createComment(
                mentee.getId(),
                post.getId(),
                new ForumCommentUpsertRequest("Em muốn hỏi thêm", List.of(), null)
        ));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    void createComment_notificationFailure_shouldNotRollbackComment() {
        ForumPost post = ForumPost.builder()
                .id(UuidUtil.generateUuidV7())
                .authorUser(User.builder()
                        .id(UuidUtil.generateUuidV7())
                        .email("author@test.com")
                        .fullName("Forum Author")
                        .status(UserStatus.ACTIVE)
                        .roles(Set.of(RoleCode.MENTEE))
                        .build())
                .helpTopic(helpTopic)
                .title("Need help")
                .content("content")
                .status(ForumPostStatus.PUBLISHED)
                .build();
        ForumCommentUpsertRequest request = new ForumCommentUpsertRequest("Em muốn hỏi thêm", List.of(), null);

        when(userRepository.findById(mentee.getId())).thenReturn(Optional.of(mentee));
        when(forumPostRepository.findByIdForUpdate(post.getId())).thenReturn(Optional.of(post));
        when(forumCommentRepository.save(any(ForumComment.class))).thenAnswer(inv -> {
            ForumComment comment = inv.getArgument(0);
            comment.setId(UuidUtil.generateUuidV7());
            return comment;
        });
        when(forumPostRepository.save(any(ForumPost.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new DataIntegrityViolationException("notification schema mismatch"))
                .when(notificationService).createNotification(any(), any(), any(), any(), any(), any());

        var response = forumPostService.createComment(mentee.getId(), post.getId(), request);

        assertEquals("Em muốn hỏi thêm", response.content());
        verify(forumCommentRepository).save(any(ForumComment.class));
        verify(forumPostRepository).save(any(ForumPost.class));
    }

    @Test
    void createPost_htmlInput_shouldThrowBadRequest() {
        when(userRepository.findById(mentee.getId())).thenReturn(Optional.of(mentee));
        when(tagRepository.findById(helpTopic.getId())).thenReturn(Optional.of(helpTopic));
        when(forumTextPolicy.requirePlainText(eq("<b>Hello</b>"), eq("Tiêu đề bài viết")))
                .thenThrow(new BaseException(ErrorCode.BAD_REQUEST, "Tiêu đề bài viết chỉ hỗ trợ plain text, không chấp nhận HTML"));

        BaseException ex = assertThrows(BaseException.class, () -> forumPostService.createPost(
                mentee.getId(),
                new ForumPostUpsertRequest("<b>Hello</b>", "content", helpTopic.getId(), List.of())
        ));

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void upsertReaction_existingLike_shouldBeIdempotent() {
        ForumPost post = ForumPost.builder()
                .id(UuidUtil.generateUuidV7())
                .authorUser(mentee)
                .helpTopic(helpTopic)
                .title("Need help")
                .content("content")
                .status(ForumPostStatus.PUBLISHED)
                .reactionCount(1)
                .build();
        ForumPostReaction reaction = ForumPostReaction.builder()
                .id(UuidUtil.generateUuidV7())
                .post(post)
                .user(mentee)
                .reactionType(ForumReactionType.LIKE)
                .build();

        when(userRepository.findById(mentee.getId())).thenReturn(Optional.of(mentee));
        when(forumPostRepository.findByIdForUpdate(post.getId())).thenReturn(Optional.of(post));
        when(forumPostReactionRepository.findByPostIdAndUserId(post.getId(), mentee.getId())).thenReturn(Optional.of(reaction));

        var response = forumPostService.upsertReaction(mentee.getId(), post.getId(), new ForumReactionRequest(ForumReactionType.LIKE));

        assertEquals(1, response.reactionCount());
        verify(forumPostReactionRepository, never()).save(any(ForumPostReaction.class));
    }

    @Test
    void upsertReaction_whenRateLimited_shouldThrowTooManyRequests() {
        when(userRepository.findById(mentee.getId())).thenReturn(Optional.of(mentee));
        doThrow(new BaseException(ErrorCode.TOO_MANY_REQUESTS, "Bạn đang thả hoặc bỏ reaction quá nhanh, vui lòng thử lại sau"))
                .when(forumAbuseGuardService).checkAndLog(mentee, ForumActionType.TOGGLE_REACTION);

        BaseException ex = assertThrows(BaseException.class, () -> forumPostService.upsertReaction(
                mentee.getId(),
                UuidUtil.generateUuidV7(),
                new ForumReactionRequest(ForumReactionType.LIKE)
        ));

        assertEquals(ErrorCode.TOO_MANY_REQUESTS, ex.getErrorCode());
    }

    @Test
    void deleteComment_ownedComment_shouldDecreaseCount() {
        ForumPost post = ForumPost.builder()
                .id(UuidUtil.generateUuidV7())
                .authorUser(mentee)
                .helpTopic(helpTopic)
                .title("Need help")
                .content("content")
                .status(ForumPostStatus.PUBLISHED)
                .commentCount(2)
                .build();
        ForumComment comment = ForumComment.builder()
                .id(UuidUtil.generateUuidV7())
                .post(post)
                .authorUser(mentee)
                .content("comment")
                .status(ForumCommentStatus.VISIBLE)
                .build();

        when(userRepository.findById(mentee.getId())).thenReturn(Optional.of(mentee));
        when(forumCommentRepository.findByIdForUpdate(comment.getId())).thenReturn(Optional.of(comment));
        when(forumPostRepository.findByIdForUpdate(post.getId())).thenReturn(Optional.of(post));

        forumPostService.deleteComment(mentee.getId(), comment.getId());

        assertEquals(1, post.getCommentCount());
        verify(forumCommentRepository).delete(comment);
        verify(forumPostRepository).save(eq(post));
    }
}
