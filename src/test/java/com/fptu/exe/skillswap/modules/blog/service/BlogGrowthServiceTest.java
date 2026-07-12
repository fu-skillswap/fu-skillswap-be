package com.fptu.exe.skillswap.modules.blog.service;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.blog.domain.BlogCategory;
import com.fptu.exe.skillswap.modules.blog.domain.BlogCategoryFollow;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPost;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPostStatus;
import com.fptu.exe.skillswap.modules.blog.domain.BlogTag;
import com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility;
import com.fptu.exe.skillswap.modules.blog.repository.BlogBookmarkRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogCategoryFollowRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogCategoryRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogPostLikeRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogPostRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogTagFollowRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlogGrowthServiceTest {

    @Mock private BlogPostRepository blogPostRepository;
    @Mock private BlogPostLikeRepository blogPostLikeRepository;
    @Mock private BlogBookmarkRepository blogBookmarkRepository;
    @Mock private BlogCategoryFollowRepository blogCategoryFollowRepository;
    @Mock private BlogTagFollowRepository blogTagFollowRepository;
    @Mock private BlogCategoryRepository blogCategoryRepository;
    @Mock private BlogTagRepository blogTagRepository;
    @Mock private BlogMapper blogMapper;
    @Mock private CursorCodec cursorCodec;
    @Mock private MentorContentAccessService mentorContentAccessService;
    @Mock private InternalTelemetryService internalTelemetryService;
    @Mock private EntityManager entityManager;

    private BlogService service;
    private UUID userId;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        service = new BlogService(
                blogPostRepository,
                blogPostLikeRepository,
                blogBookmarkRepository,
                blogCategoryFollowRepository,
                blogTagFollowRepository,
                blogCategoryRepository,
                blogTagRepository,
                blogMapper,
                cursorCodec,
                new BlogContentPolicy(),
                mentorContentAccessService,
                internalTelemetryService,
                entityManager
        );
        userId = UUID.fromString("018f3abf-0a22-7112-9748-6cf000c47b6e");
        principal = UserPrincipal.create(userId, "user@example.com", List.of(RoleCode.MENTEE));
    }

    @Test
    void followCategory_shouldSaveOnceAndReturnMyFollows() {
        UUID categoryId = UUID.fromString("018f3abf-0a22-7122-9748-6cf000c47b6e");
        BlogCategory category = BlogCategory.builder()
                .id(categoryId)
                .code("BACKEND")
                .name("Backend")
                .slug("backend")
                .active(true)
                .displayOrder(1)
                .build();
        when(blogCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(blogCategoryFollowRepository.existsByUserIdAndCategoryId(userId, categoryId)).thenReturn(false);
        when(entityManager.getReference(User.class, userId)).thenReturn(new User());
        when(blogCategoryFollowRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(BlogCategoryFollow.builder().category(category).build()));
        when(blogTagFollowRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        when(blogMapper.toCategory(category)).thenCallRealMethod();

        var response = service.followCategory(principal, categoryId);

        verify(blogCategoryFollowRepository).save(any(BlogCategoryFollow.class));
        assertEquals(1, response.categories().size());
    }

    @Test
    void personalizedFeed_withoutFollows_shouldFallbackToLatestAccessiblePosts() {
        UUID postId = UUID.fromString("018f3abf-0a22-7132-9748-6cf000c47b6e");
        User author = new User();
        author.setId(UUID.fromString("018f3abf-0a22-7152-9748-6cf000c47b6e"));
        author.setFullName("Author");
        BlogPost post = BlogPost.builder()
                .id(postId)
                .authorUser(author)
                .title("Post")
                .slug("post")
                .status(BlogPostStatus.PUBLISHED)
                .visibility(BlogVisibility.PUBLIC)
                .publishedAt(LocalDateTime.now())
                .build();
        when(blogCategoryFollowRepository.findCategoryIdsByUserId(userId)).thenReturn(Set.of());
        when(blogTagFollowRepository.findTagIdsByUserId(userId)).thenReturn(Set.of());
        when(blogPostRepository.findPublicWindow(any(), any(), any(), any(), any(), any(), any(), any(Integer.class)))
                .thenReturn(List.of(post));
        when(blogPostLikeRepository.findLikedPostIds(userId, List.of(postId))).thenReturn(Set.of());
        when(blogBookmarkRepository.findBookmarkedPostIds(userId, List.of(postId))).thenReturn(Set.of());
        when(mentorContentAccessService.getBlogAuthorSummaries(any())).thenReturn(Map.of());
        when(blogMapper.toCard(any(), any(), any())).thenCallRealMethod();

        var response = service.personalizedFeed(principal, null, 20);

        assertEquals(1, response.items().size());
        verify(internalTelemetryService).record(any(), any(), any(), any(), any());
    }
}
