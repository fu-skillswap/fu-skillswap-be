package com.fptu.exe.skillswap.modules.blog.service;

import com.fptu.exe.skillswap.modules.blog.domain.BlogAudienceType;
import com.fptu.exe.skillswap.modules.blog.domain.BlogCategory;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPost;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPostStatus;
import com.fptu.exe.skillswap.modules.blog.domain.BlogTag;
import com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility;
import com.fptu.exe.skillswap.modules.blog.dto.BlogCategoryResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogPostCardResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogPostDetailResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogTagResponse;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogCategoryUpsertRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogFeatureRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogPostUpsertRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogTagUpsertRequest;
import com.fptu.exe.skillswap.modules.blog.event.BlogPostPublishedEvent;
import com.fptu.exe.skillswap.modules.blog.repository.BlogCategoryRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogPostRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogTagRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.shared.cursor.CursorCodec;
import com.fptu.exe.skillswap.shared.cursor.CursorTokenPayload;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import com.fptu.exe.skillswap.shared.util.UuidUtil;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminBlogService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final String DIRECTION_NEXT = "NEXT";

    private final BlogPostRepository blogPostRepository;
    private final BlogCategoryRepository blogCategoryRepository;
    private final BlogTagRepository blogTagRepository;
    private final BlogMapper blogMapper;
    private final BlogContentPolicy contentPolicy;
    private final CursorCodec cursorCodec;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public CursorPageResponse<BlogPostCardResponse> listPosts(String cursor,
                                                              Integer limit,
                                                              BlogPostStatus status,
                                                              UUID authorUserId,
                                                              UUID categoryId,
                                                              UUID tagId,
                                                              String keyword) {
        int resolvedLimit = resolveLimit(limit);
        String keywordPattern = likePattern(keyword);
        String filterHash = filterHash("blog-posts:admin|status=" + normalize(status)
                + "|authorUserId=" + normalize(authorUserId)
                + "|categoryId=" + normalize(categoryId)
                + "|tagId=" + normalize(tagId)
                + "|keyword=" + normalizeKeyword(keyword));
        DecodedCursor decoded = decodeCursor(cursor, filterHash);
        List<BlogPost> window = blogPostRepository.findAdminWindow(
                status,
                authorUserId,
                categoryId,
                tagId,
                keywordPattern,
                decoded.sortTime(),
                decoded.postId(),
                resolvedLimit + 1
        );
        boolean hasNext = window.size() > resolvedLimit;
        List<BlogPost> items = hasNext ? window.subList(0, resolvedLimit) : window;
        String nextCursor = hasNext && !items.isEmpty()
                ? encodeCursor(items.get(items.size() - 1).getUpdatedAt(), items.get(items.size() - 1).getId(), filterHash)
                : null;
        return CursorPageResponse.<BlogPostCardResponse>builder()
                .items(items.stream().map(blogMapper::toCard).toList())
                .nextCursor(nextCursor)
                .prevCursor(null)
                .hasNext(hasNext)
                .hasPrev(false)
                .limit(resolvedLimit)
                .build();
    }

    @Transactional(readOnly = true)
    public BlogPostDetailResponse getPost(UUID postId) {
        return blogMapper.toDetail(loadPost(postId));
    }

    @Transactional
    public BlogPostDetailResponse createPost(UUID authorUserId, BlogPostUpsertRequest request) {
        requireRequest(request);
        String title = contentPolicy.cleanRequired(request.title(), "Tiêu đề bài blog");
        String slug = uniqueSlug(contentPolicy.cleanNullable(request.slug()) == null ? title : request.slug(), null);
        BlogPost post = BlogPost.builder()
                .authorUser(entityManager.getReference(User.class, authorUserId))
                .title(title)
                .slug(slug)
                .status(BlogPostStatus.DRAFT)
                .build();
        applyEditableFields(post, request, false);
        return blogMapper.toDetail(blogPostRepository.save(post));
    }

    @Transactional
    public BlogPostDetailResponse updatePost(UUID postId, BlogPostUpsertRequest request) {
        requireRequest(request);
        BlogPost post = loadPost(postId);
        applyEditableFields(post, request, true);
        return blogMapper.toDetail(blogPostRepository.save(post));
    }

    @Transactional
    public BlogPostDetailResponse publish(UUID postId) {
        BlogPost post = loadPost(postId);
        if (!contentPolicy.hasText(post.getContentMarkdown())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Không thể publish bài blog chưa có nội dung");
        }
        BlogPostStatus previousStatus = post.getStatus();
        LocalDateTime now = DateTimeUtil.now();
        if (post.getPublishedAt() == null) {
            post.setPublishedAt(now);
        }
        post.setLastPublishedAt(now);
        post.setStatus(BlogPostStatus.PUBLISHED);
        post.setSlugLocked(true);
        BlogPost saved = blogPostRepository.save(post);
        if (previousStatus != BlogPostStatus.PUBLISHED) {
            eventPublisher.publishEvent(new BlogPostPublishedEvent(
                    UuidUtil.generateUuidV7(),
                    saved.getId(),
                    saved.getSlug(),
                    saved.getTitle(),
                    saved.getAuthorUser().getId(),
                    saved.getAuthorUser().getFullName(),
                    saved.getVisibility(),
                    saved.getCategories().stream().map(BlogCategory::getId).collect(Collectors.toSet()),
                    saved.getTags().stream().map(BlogTag::getId).collect(Collectors.toSet()),
                    now
            ));
        }
        return blogMapper.toDetail(saved);
    }

    @Transactional
    public BlogPostDetailResponse archive(UUID postId) {
        BlogPost post = loadPost(postId);
        post.setStatus(BlogPostStatus.ARCHIVED);
        post.setFeatured(false);
        post.setFeaturedOrder(null);
        post.setFeaturedUntil(null);
        return blogMapper.toDetail(blogPostRepository.save(post));
    }

    @Transactional
    public BlogPostDetailResponse feature(UUID postId, BlogFeatureRequest request) {
        BlogPost post = loadPost(postId);
        if (post.getStatus() != BlogPostStatus.PUBLISHED) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Chỉ bài đã publish mới được feature");
        }
        post.setFeatured(true);
        post.setFeaturedOrder(request == null ? null : request.featuredOrder());
        post.setFeaturedUntil(request == null ? null : request.featuredUntil());
        return blogMapper.toDetail(blogPostRepository.save(post));
    }

    @Transactional
    public BlogPostDetailResponse unfeature(UUID postId) {
        BlogPost post = loadPost(postId);
        post.setFeatured(false);
        post.setFeaturedOrder(null);
        post.setFeaturedUntil(null);
        return blogMapper.toDetail(blogPostRepository.save(post));
    }

    @Transactional
    public void deletePost(UUID postId) {
        blogPostRepository.delete(loadPost(postId));
    }

    @Transactional(readOnly = true)
    public List<BlogCategoryResponse> categories() {
        return blogCategoryRepository.findAll().stream()
                .map(blogMapper::toCategory)
                .toList();
    }

    @Transactional
    public BlogCategoryResponse upsertCategory(BlogCategoryUpsertRequest request) {
        String code = contentPolicy.cleanRequired(request.code(), "Mã category").toUpperCase();
        BlogCategory category = blogCategoryRepository.findByCodeIgnoreCase(code)
                .orElseGet(BlogCategory::new);
        category.setCode(code);
        category.setName(contentPolicy.cleanRequired(request.name(), "Tên category"));
        category.setSlug(uniqueCategorySlug(contentPolicy.cleanNullable(request.slug()) == null ? request.name() : request.slug(), category.getId()));
        category.setDescription(contentPolicy.cleanNullable(request.description()));
        category.setActive(request.active() == null || request.active());
        category.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        return blogMapper.toCategory(blogCategoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<BlogTagResponse> tags() {
        return blogTagRepository.findAll().stream()
                .map(blogMapper::toTag)
                .toList();
    }

    @Transactional
    public BlogTagResponse upsertTag(BlogTagUpsertRequest request) {
        String slug = contentPolicy.slugify(contentPolicy.cleanNullable(request.slug()) == null ? request.name() : request.slug());
        BlogTag tag = blogTagRepository.findBySlug(slug).orElseGet(BlogTag::new);
        tag.setName(contentPolicy.cleanRequired(request.name(), "Tên tag"));
        tag.setSlug(slug);
        tag.setActive(request.active() == null || request.active());
        return blogMapper.toTag(blogTagRepository.save(tag));
    }

    private void applyEditableFields(BlogPost post, BlogPostUpsertRequest request, boolean existingPost) {
        post.setTitle(contentPolicy.cleanRequired(request.title(), "Tiêu đề bài blog"));
        if (contentPolicy.hasText(request.slug())) {
            if (post.isSlugLocked() && !post.getSlug().equals(contentPolicy.slugify(request.slug()))) {
                throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Slug đã bị khóa sau khi publish");
            }
            post.setSlug(uniqueSlug(request.slug(), existingPost ? post.getId() : null));
        } else if (!post.isSlugLocked() && !contentPolicy.hasText(post.getSlug())) {
            post.setSlug(uniqueSlug(post.getTitle(), existingPost ? post.getId() : null));
        }
        contentPolicy.validateMarkdown(request.contentMarkdown());
        post.setExcerpt(contentPolicy.cleanNullable(request.excerpt()));
        post.setContentMarkdown(contentPolicy.cleanNullable(request.contentMarkdown()));
        post.setContentHash(contentPolicy.sha256Hex(request.contentMarkdown()));
        post.setReadingTimeMinutes(contentPolicy.readingTimeMinutes(request.contentMarkdown()));
        post.setCoverImageUrl(contentPolicy.cleanNullable(request.coverImageUrl()));
        post.setCoverImageObjectKey(contentPolicy.cleanNullable(request.coverImageObjectKey()));
        post.setOgImageUrl(contentPolicy.cleanNullable(request.ogImageUrl()));
        post.setOgImageObjectKey(contentPolicy.cleanNullable(request.ogImageObjectKey()));
        post.setAudienceType(request.audienceType() == null ? BlogAudienceType.BOTH : request.audienceType());
        post.setVisibility(request.visibility() == null ? BlogVisibility.PUBLIC : request.visibility());
        post.setSeoTitle(contentPolicy.cleanNullable(request.seoTitle()));
        post.setSeoDescription(contentPolicy.cleanNullable(request.seoDescription()));
        post.setCanonicalUrl(validateCanonicalUrl(request.canonicalUrl()));
        post.setCategories(new LinkedHashSet<>(loadCategories(request.categoryIds())));
        post.setTags(new LinkedHashSet<>(loadTags(request.tagIds())));
    }

    private List<BlogCategory> loadCategories(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<UUID> uniqueIds = new LinkedHashSet<>(ids);
        List<BlogCategory> categories = blogCategoryRepository.findByIdInAndActiveTrue(uniqueIds);
        if (categories.size() != uniqueIds.size()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Một hoặc nhiều category không tồn tại hoặc chưa active");
        }
        return categories;
    }

    private List<BlogTag> loadTags(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<UUID> uniqueIds = new LinkedHashSet<>(ids);
        List<BlogTag> tags = blogTagRepository.findByIdInAndActiveTrue(uniqueIds);
        if (tags.size() != uniqueIds.size()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Một hoặc nhiều tag không tồn tại hoặc chưa active");
        }
        return tags;
    }

    private BlogPost loadPost(UUID postId) {
        return blogPostRepository.findById(postId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy bài blog"));
    }

    private void requireRequest(BlogPostUpsertRequest request) {
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Request bài blog không được để trống");
        }
    }

    private String validateCanonicalUrl(String canonicalUrl) {
        String cleaned = contentPolicy.cleanNullable(canonicalUrl);
        if (cleaned == null) {
            return null;
        }
        if (!cleaned.startsWith("https://") && !cleaned.startsWith("http://")) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "canonicalUrl phải là URL http hoặc https");
        }
        return cleaned;
    }

    private String uniqueSlug(String raw, UUID currentPostId) {
        String base = contentPolicy.slugify(raw);
        String slug = base;
        int suffix = 2;
        while (currentPostId == null ? blogPostRepository.existsBySlug(slug) : blogPostRepository.existsBySlugAndIdNot(slug, currentPostId)) {
            slug = base + "-" + suffix++;
        }
        return slug;
    }

    private String uniqueCategorySlug(String raw, UUID currentCategoryId) {
        String base = contentPolicy.slugify(raw);
        String slug = base;
        int suffix = 2;
        while (currentCategoryId == null
                ? blogCategoryRepository.existsBySlug(slug)
                : blogCategoryRepository.existsBySlugAndIdNot(slug, currentCategoryId)) {
            slug = base + "-" + suffix++;
        }
        return slug;
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String likePattern(String keyword) {
        return contentPolicy.hasText(keyword) ? "%" + keyword.trim().toLowerCase() + "%" : null;
    }

    private String normalizeKeyword(String keyword) {
        return contentPolicy.hasText(keyword) ? keyword.trim().toLowerCase() : "";
    }

    private String normalize(Object value) {
        return value == null ? "" : value.toString();
    }

    private DecodedCursor decodeCursor(String cursor, String expectedFilterHash) {
        if (!contentPolicy.hasText(cursor)) {
            return new DecodedCursor(null, null);
        }
        CursorTokenPayload payload = cursorCodec.decode(cursor);
        if (!expectedFilterHash.equals(payload.filterHash())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cursor không khớp với bộ lọc hiện tại");
        }
        try {
            return new DecodedCursor(LocalDateTime.parse(payload.sortKey()), UUID.fromString(payload.secondaryKey()));
        } catch (RuntimeException ex) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cursor blog không hợp lệ");
        }
    }

    private String encodeCursor(LocalDateTime sortTime, UUID postId, String filterHash) {
        return cursorCodec.encode(CursorTokenPayload.builder()
                .sortKey(sortTime.toString())
                .secondaryKey(postId.toString())
                .direction(DIRECTION_NEXT)
                .filterHash(filterHash)
                .issuedAt(Instant.now())
                .build());
    }

    private String filterHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Không thể tạo filter hash", ex);
        }
    }

    private record DecodedCursor(LocalDateTime sortTime, UUID postId) {
    }
}
