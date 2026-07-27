package com.fptu.exe.skillswap.modules.blog.service;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPost;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPostStatus;
import com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility;
import com.fptu.exe.skillswap.modules.blog.repository.BlogBookmarkRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogCategoryFollowRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogCategoryRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogPostLikeRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogPostRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogMentorFollowRepository;
import com.fptu.exe.skillswap.modules.booking.service.BookingEligibilityPolicy;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogTagRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.mentor.service.MentorContentAccessService;
import com.fptu.exe.skillswap.modules.system.service.InternalTelemetryService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.cursor.CursorCodec;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlogEngagementServiceTest {

    @Mock private BlogPostRepository blogPostRepository;
    @Mock private BlogPostLikeRepository blogPostLikeRepository;
    @Mock private BlogBookmarkRepository blogBookmarkRepository;
    @Mock private BlogCategoryFollowRepository blogCategoryFollowRepository;
    @Mock private BlogMentorFollowRepository blogMentorFollowRepository;
    @Mock private BlogCategoryRepository blogCategoryRepository;
    @Mock private BlogTagRepository blogTagRepository;
    @Mock private BlogMapper blogMapper;
    @Mock private CursorCodec cursorCodec;
    @Mock private MentorContentAccessService mentorContentAccessService;
    @Mock private InternalTelemetryService internalTelemetryService;
    @Mock private EntityManager entityManager;
    @Mock private BlogTrendingCache trendingCache;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private BookingEligibilityPolicy bookingEligibilityPolicy;
    @Mock private MentorProfileRepository mentorProfileRepository;

    private BlogService service;
    private UUID userId;
    private UUID postId;
    private BlogPost post;
    private User author;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        service = new BlogService(
                blogPostRepository,
                blogPostLikeRepository,
                blogBookmarkRepository,
                blogCategoryFollowRepository,
                blogMentorFollowRepository,
                blogCategoryRepository,
                blogTagRepository,
                blogMapper,
                cursorCodec,
                new BlogContentPolicy(),
                mentorContentAccessService,
                internalTelemetryService,
                entityManager,
                trendingCache,
                eventPublisher,
                bookingEligibilityPolicy,
                mentorProfileRepository
        );
        userId = UUID.fromString("018f3abf-0a22-7112-9748-6cf000c47b6e");
        postId = UUID.fromString("018f3abf-0a22-7132-9748-6cf000c47b6e");
        principal = UserPrincipal.create(userId, "user@example.com", List.of(RoleCode.MENTEE));
        author = new User();
        author.setId(UUID.fromString("018f3abf-0a22-7152-9748-6cf000c47b6e"));
        author.setFullName("Author");
        author.setRoles(Set.of(RoleCode.MENTOR));
        post = BlogPost.builder()
                .id(postId)
                .authorUser(author)
                .title("Post")
                .slug("post")
                .status(BlogPostStatus.PUBLISHED)
                .visibility(BlogVisibility.PUBLIC)
                .publishedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void like_existingLike_shouldNotIncrementCounter() {
        when(blogPostRepository.findByIdForEngagementUpdate(postId)).thenReturn(Optional.of(post));
        when(blogPostRepository.findById(postId)).thenReturn(Optional.of(post));
        when(blogPostLikeRepository.existsByPostIdAndUserId(postId, userId)).thenReturn(true);
        when(blogBookmarkRepository.existsByPostIdAndUserId(postId, userId)).thenReturn(false);

        service.like(principal, postId);

        verify(blogPostRepository, never()).incrementLikeCount(postId);
        verify(internalTelemetryService, never()).record(eq("BLOG_LIKE"), any(), any(), any(), any());
    }

    @Test
    void bookmark_newBookmark_shouldIncrementCounterAtomically() {
        when(blogPostRepository.findByIdForEngagementUpdate(postId)).thenReturn(Optional.of(post));
        when(blogPostRepository.findById(postId)).thenReturn(Optional.of(post));
        when(blogBookmarkRepository.existsByPostIdAndUserId(postId, userId)).thenReturn(false, true);
        when(blogPostLikeRepository.existsByPostIdAndUserId(postId, userId)).thenReturn(false);
        when(entityManager.getReference(BlogPost.class, postId)).thenReturn(post);
        when(entityManager.getReference(User.class, userId)).thenReturn(new User());

        service.bookmark(principal, postId);

        verify(blogBookmarkRepository).save(any());
        verify(blogPostRepository).incrementBookmarkCount(postId);
        verify(internalTelemetryService).record(eq("BLOG_BOOKMARK"), eq(userId), eq("BLOG_POST"), eq(postId), any());
    }
}
