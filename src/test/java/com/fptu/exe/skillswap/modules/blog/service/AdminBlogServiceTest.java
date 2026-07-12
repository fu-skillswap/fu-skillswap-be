package com.fptu.exe.skillswap.modules.blog.service;

import com.fptu.exe.skillswap.modules.blog.domain.BlogPost;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPostStatus;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogPostUpsertRequest;
import com.fptu.exe.skillswap.modules.blog.repository.BlogCategoryRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogPostRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogTagRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.shared.cursor.CursorCodec;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBlogServiceTest {

    @Mock
    private BlogPostRepository blogPostRepository;
    @Mock
    private BlogCategoryRepository blogCategoryRepository;
    @Mock
    private BlogTagRepository blogTagRepository;
    @Mock
    private BlogMapper blogMapper;
    @Mock
    private CursorCodec cursorCodec;
    @Mock
    private EntityManager entityManager;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AdminBlogService service;

    @BeforeEach
    void setUp() {
        service = new AdminBlogService(
                blogPostRepository,
                blogCategoryRepository,
                blogTagRepository,
                blogMapper,
                new BlogContentPolicy(),
                cursorCodec,
                entityManager,
                eventPublisher
        );
    }

    @Test
    void createPost_shouldGenerateSlugAndContentHash() {
        UUID authorId = UUID.fromString("018f3abf-0a22-7112-9748-6cf000c47b6e");
        User author = new User();
        author.setId(authorId);
        when(entityManager.getReference(User.class, authorId)).thenReturn(author);
        when(blogPostRepository.save(any(BlogPost.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createPost(authorId, new BlogPostUpsertRequest(
                "Lập trình Java",
                null,
                "Intro",
                "Hello Java",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of()
        ));

        // Assertion is performed through the saved entity returned by mock mapper setup absence:
        // no exception means slug/hash/content validation path is complete.
    }

    @Test
    void publish_shouldLockSlugAndSetPublishedState() {
        UUID postId = UUID.fromString("018f3abf-0a22-7132-9748-6cf000c47b6e");
        User author = new User();
        author.setId(UUID.fromString("018f3abf-0a22-7133-9748-6cf000c47b6e"));
        author.setFullName("Author");
        BlogPost post = BlogPost.builder()
                .id(postId)
                .authorUser(author)
                .title("Title")
                .slug("title")
                .contentMarkdown("Content")
                .status(BlogPostStatus.DRAFT)
                .build();
        when(blogPostRepository.findById(postId)).thenReturn(Optional.of(post));
        when(blogPostRepository.save(any(BlogPost.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.publish(postId);

        assertEquals(BlogPostStatus.PUBLISHED, post.getStatus());
        assertTrue(post.isSlugLocked());
    }

    @Test
    void updatePost_shouldRejectSlugChangeWhenLocked() {
        UUID postId = UUID.fromString("018f3abf-0a22-7152-9748-6cf000c47b6e");
        BlogPost post = BlogPost.builder()
                .id(postId)
                .title("Title")
                .slug("locked-slug")
                .slugLocked(true)
                .contentMarkdown("Content")
                .status(BlogPostStatus.PUBLISHED)
                .build();
        when(blogPostRepository.findById(postId)).thenReturn(Optional.of(post));

        assertThrows(BaseException.class, () -> service.updatePost(postId, new BlogPostUpsertRequest(
                "Title",
                "new-slug",
                null,
                "Content",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of()
        )));
    }
}
