package com.fptu.exe.skillswap.modules.blog.service;

import com.fptu.exe.skillswap.modules.blog.domain.BlogAuthorType;
import com.fptu.exe.skillswap.modules.blog.domain.BlogCategory;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPost;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPostStatus;
import com.fptu.exe.skillswap.modules.blog.domain.BlogTag;
import com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility;
import com.fptu.exe.skillswap.modules.blog.dto.AdminBlogPostCardResponse;
import com.fptu.exe.skillswap.modules.blog.dto.AdminBlogPostDetailResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogCategoryResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogTagResponse;
import com.fptu.exe.skillswap.modules.blog.dto.request.AdminBlogPostCreateRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.AdminMentorBlogModerationRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogExpectedVersionRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.AdminBlogPostUpdateRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.AdminBlogPostWriteRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.AdminBlogTagWriteRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogCategoryUpsertRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogFeatureRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogTagUpsertRequest;
import com.fptu.exe.skillswap.modules.blog.event.BlogPostPublishedEvent;
import com.fptu.exe.skillswap.modules.blog.event.BlogTaxonomyChangedEvent;
import com.fptu.exe.skillswap.modules.blog.event.BlogTrendingRankingChangedEvent;
import com.fptu.exe.skillswap.shared.exception.VersionConflictException;
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
import com.fptu.exe.skillswap.modules.admin.port.AuditLogPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
import java.util.Objects;
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
    private final AuditLogPort adminAuditWriterService;

    @Transactional(readOnly = true)
    public CursorPageResponse<AdminBlogPostCardResponse> listPosts(String cursor,
                                                              Integer limit,
                                                              BlogPostStatus status,
                                                              UUID authorUserId,
                                                              UUID categoryId,
                                                              UUID tagId,
                                                              String keyword,
                                                              boolean deleted) {
        int resolvedLimit = resolveLimit(limit);
        if (deleted) {
            List<BlogPost> window = blogPostRepository.findDeletedForAdmin(org.springframework.data.domain.PageRequest.of(0, resolvedLimit + 1));
            boolean hasNext = window.size() > resolvedLimit;
            List<BlogPost> items = hasNext ? window.subList(0, resolvedLimit) : window;
            return CursorPageResponse.<AdminBlogPostCardResponse>builder()
                    .items(mapAdminCards(items))
                    .nextCursor(null)
                    .prevCursor(null)
                    .hasNext(hasNext)
                    .hasPrev(false)
                    .limit(resolvedLimit)
                    .build();
        }
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
        return CursorPageResponse.<AdminBlogPostCardResponse>builder()
                .items(mapAdminCards(items))
                .nextCursor(nextCursor)
                .prevCursor(null)
                .hasNext(hasNext)
                .hasPrev(false)
                .limit(resolvedLimit)
                .build();
    }

    @Transactional(readOnly = true)
    public AdminBlogPostDetailResponse getPost(UUID postId) {
        BlogPost post = blogPostRepository.findById(postId)
                .or(() -> blogPostRepository.findDeletedByIdForAdmin(postId))
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy bài blog"));
        return blogMapper.toAdminDetail(post, null);
    }

    @Transactional
    public AdminBlogPostDetailResponse createPost(UUID authorUserId, AdminBlogPostCreateRequest request) {
        requireRequest(request);
        String title = contentPolicy.cleanRequired(request.title(), "Tiêu đề bài blog");
        String slug = uniqueSlug(contentPolicy.cleanNullable(request.slug()) == null ? title : request.slug(), null);
        BlogPost post = BlogPost.builder()
                .authorUser(entityManager.getReference(User.class, authorUserId))
                .authorType(BlogAuthorType.PLATFORM)
                .title(title)
                .slug(slug)
                .status(BlogPostStatus.DRAFT)
                .build();
        applyEditableFields(post, request, false);
        BlogPost saved = blogPostRepository.save(post);
        entityManager.flush();
        return blogMapper.toAdminDetail(saved, null);
    }

    @Transactional
    public AdminBlogPostDetailResponse updatePost(UUID postId, AdminBlogPostUpdateRequest request) {
        requireRequest(request);
        BlogPost post = loadPost(postId);
        if (post.getAuthorType() != BlogAuthorType.PLATFORM) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Không thể sửa nội dung bài viết của mentor từ CMS platform");
        }
        if (!Objects.equals(request.expectedVersion(), post.getVersion())) {
            throw new VersionConflictException(
                    ErrorCode.BLOG_POST_VERSION_CONFLICT,
                    ErrorCode.BLOG_POST_VERSION_CONFLICT.getMessage(),
                    postId,
                    request.expectedVersion(),
                    post.getVersion()
            );
        }
        BlogVisibility previousVisibility = post.getVisibility();
        applyEditableFields(post, request, true);
        BlogPost saved = blogPostRepository.save(post);
        try {
            entityManager.flush();
        } catch (OptimisticLockException | ObjectOptimisticLockingFailureException exception) {
            throw new VersionConflictException(
                    ErrorCode.BLOG_POST_VERSION_CONFLICT,
                    ErrorCode.BLOG_POST_VERSION_CONFLICT.getMessage(),
                    postId,
                    request.expectedVersion(),
                    null
            );
        }
        if (saved.getStatus() == BlogPostStatus.PUBLISHED
                && previousVisibility != saved.getVisibility()) {
            signalTrendingChange(saved.getId());
        }
        return blogMapper.toAdminDetail(saved, null);
    }

    @Transactional
    public AdminBlogPostDetailResponse moderateMentorPost(UUID adminId, UUID postId, AdminMentorBlogModerationRequest request) {
        BlogPost post = loadPost(postId);
        if (post.getAuthorType() != BlogAuthorType.MENTOR) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Endpoint moderation chỉ áp dụng cho bài viết mentor");
        }
        if (!Objects.equals(post.getVersion(), request.expectedVersion())) {
            throw new VersionConflictException(ErrorCode.BLOG_POST_VERSION_CONFLICT, ErrorCode.BLOG_POST_VERSION_CONFLICT.getMessage(),
                    postId, request.expectedVersion(), post.getVersion());
        }
        BlogVisibility oldVisibility = post.getVisibility();
        if (request.visibility() != null) post.setVisibility(request.visibility());
        if (request.categoryIds() != null) post.setCategories(new LinkedHashSet<>(loadCategories(request.categoryIds())));
        if (request.tagIds() != null) post.setTags(new LinkedHashSet<>(loadTags(request.tagIds())));
        if (request.seoTitle() != null) post.setSeoTitle(contentPolicy.cleanNullable(request.seoTitle()));
        if (request.seoDescription() != null) post.setSeoDescription(contentPolicy.cleanNullable(request.seoDescription()));
        if (Boolean.TRUE.equals(request.archive())) {
            post.setStatus(BlogPostStatus.ARCHIVED); post.setFeatured(false); post.setFeaturedOrder(null); post.setFeaturedUntil(null);
        }
        BlogPost saved = blogPostRepository.save(post);
        adminAuditWriterService.writeOperatorEvent(adminId, "BLOG_POST", postId, "BLOG_MENTOR_MODERATED",
                java.util.Map.of("visibility", oldVisibility.name()), java.util.Map.of("visibility", saved.getVisibility().name(), "status", saved.getStatus().name()));
        signalTrendingChange(postId);
        return blogMapper.toAdminDetail(saved, null);
    }

    @Transactional
    public AdminBlogPostDetailResponse publish(UUID postId) {
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
        entityManager.flush();
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
                    saved.getEntitledServices().stream().map(service -> service.getId()).collect(Collectors.toSet()),
                    now
            ));
        }
        signalTrendingChange(saved.getId());
        return blogMapper.toAdminDetail(saved, null);
    }

    @Transactional
    public AdminBlogPostDetailResponse archive(UUID postId) {
        BlogPost post = loadPost(postId);
        post.setStatus(BlogPostStatus.ARCHIVED);
        post.setFeatured(false);
        post.setFeaturedOrder(null);
        post.setFeaturedUntil(null);
        BlogPost saved = blogPostRepository.save(post);
        entityManager.flush();
        signalTrendingChange(saved.getId());
        return blogMapper.toAdminDetail(saved, null);
    }

    @Transactional
    public AdminBlogPostDetailResponse feature(UUID postId, BlogFeatureRequest request) {
        BlogPost post = loadPost(postId);
        if (post.getStatus() != BlogPostStatus.PUBLISHED) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Chỉ bài đã publish mới được feature");
        }
        post.setFeatured(true);
        post.setFeaturedOrder(request == null ? null : request.featuredOrder());
        post.setFeaturedUntil(request == null ? null : request.featuredUntil());
        BlogPost saved = blogPostRepository.save(post);
        entityManager.flush();
        signalTrendingChange(saved.getId());
        return blogMapper.toAdminDetail(saved, null);
    }

    @Transactional
    public AdminBlogPostDetailResponse unfeature(UUID postId) {
        BlogPost post = loadPost(postId);
        post.setFeatured(false);
        post.setFeaturedOrder(null);
        post.setFeaturedUntil(null);
        BlogPost saved = blogPostRepository.save(post);
        entityManager.flush();
        signalTrendingChange(saved.getId());
        return blogMapper.toAdminDetail(saved, null);
    }

    @Transactional
    public AdminBlogPostDetailResponse deletePost(UUID adminId, UUID postId, BlogExpectedVersionRequest request) {
        BlogPost post = loadPost(postId);
        requireVersion(post, postId, request.expectedVersion());
        if (blogPostRepository.softDeleteByIdAndVersion(postId, request.expectedVersion()) != 1) {
            throw new VersionConflictException(ErrorCode.BLOG_POST_VERSION_CONFLICT, ErrorCode.BLOG_POST_VERSION_CONFLICT.getMessage(), postId, request.expectedVersion(), null);
        }
        post.setDeletedAt(DateTimeUtil.now());
        post.setVersion(post.getVersion() + 1);
        adminAuditWriterService.writeOperatorEvent(adminId, "BLOG_POST", postId, "BLOG_POST_DELETED", java.util.Map.of("status", post.getStatus().name()), java.util.Map.of());
        signalTrendingChange(postId);
        return blogMapper.toAdminDetail(post, null);
    }

    @Transactional
    public AdminBlogPostDetailResponse restorePost(UUID adminId, UUID postId, BlogExpectedVersionRequest request) {
        BlogPost deleted = blogPostRepository.findDeletedByIdForAdmin(postId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy bài blog đã xóa"));
        requireVersion(deleted, postId, request.expectedVersion());
        if (blogPostRepository.restoreByIdAndVersion(postId, request.expectedVersion()) != 1) {
            throw new VersionConflictException(ErrorCode.BLOG_POST_VERSION_CONFLICT, ErrorCode.BLOG_POST_VERSION_CONFLICT.getMessage(), postId, request.expectedVersion(), null);
        }
        BlogPost restored = blogPostRepository.findById(postId).orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy bài blog"));
        adminAuditWriterService.writeOperatorEvent(adminId, "BLOG_POST", postId, "BLOG_POST_RESTORED", java.util.Map.of(), java.util.Map.of("status", restored.getStatus().name()));
        signalTrendingChange(postId);
        return blogMapper.toAdminDetail(restored, null);
    }

    @Transactional(readOnly = true)
    public List<BlogCategoryResponse> categories() {
        return blogCategoryRepository.findAll().stream()
                .map(blogMapper::toCategory)
                .toList();
    }

    @Transactional
    public CategoryUpsertResult upsertCategory(BlogCategoryUpsertRequest request) {
        String code = contentPolicy.cleanRequired(request.code(), "Mã category").toUpperCase();
        var existing = blogCategoryRepository.findByCodeIgnoreCase(code);
        BlogCategory category = existing.orElseGet(BlogCategory::new);
        category.setCode(code);
        category.setName(contentPolicy.cleanRequired(request.name(), "Tên category"));
        category.setSlug(uniqueCategorySlug(contentPolicy.cleanNullable(request.slug()) == null ? request.name() : request.slug(), category.getId()));
        category.setDescription(contentPolicy.cleanNullable(request.description()));
        category.setActive(request.active() == null || request.active());
        category.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        BlogCategory saved = blogCategoryRepository.save(category);
        signalTaxonomyChange();
        return new CategoryUpsertResult(blogMapper.toCategory(saved), existing.isEmpty());
    }

    @Transactional(readOnly = true)
    public List<BlogTagResponse> tags() {
        return blogTagRepository.findAll().stream()
                .map(blogMapper::toTag)
                .toList();
    }

    @Transactional
    public BlogTagResponse createTag(AdminBlogTagWriteRequest request) {
        String slug = normalizedTagSlug(request.name(), request.slug());
        if (blogTagRepository.existsBySlug(slug)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Slug blog tag đã tồn tại");
        }
        BlogTag tag = new BlogTag();
        applyTagFields(tag, request, slug);
        BlogTag saved = blogTagRepository.save(tag);
        signalTaxonomyChange();
        return blogMapper.toTag(saved);
    }

    @Transactional
    public BlogTagResponse updateTag(UUID tagId, AdminBlogTagWriteRequest request) {
        BlogTag tag = blogTagRepository.findById(tagId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy blog tag"));
        String slug = normalizedTagSlug(request.name(), request.slug());
        if (blogTagRepository.existsBySlugAndIdNot(slug, tagId)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Slug blog tag đã tồn tại");
        }
        applyTagFields(tag, request, slug);
        BlogTag saved = blogTagRepository.save(tag);
        signalTaxonomyChange();
        return blogMapper.toTag(saved);
    }

    @Transactional
    public BlogTagResponse upsertLegacyTag(BlogTagUpsertRequest request) {
        if (request.id() != null) {
            return updateTag(request.id(), new AdminBlogTagWriteRequest(request.name(), request.slug(), request.active()));
        }
        String slug = contentPolicy.slugify(contentPolicy.cleanNullable(request.slug()) == null ? request.name() : request.slug());
        BlogTag tag = blogTagRepository.findBySlug(slug).orElseGet(BlogTag::new);
        applyTagFields(tag, new AdminBlogTagWriteRequest(request.name(), request.slug(), request.active()), slug);
        BlogTag saved = blogTagRepository.save(tag);
        signalTaxonomyChange();
        return blogMapper.toTag(saved);
    }

    private void applyEditableFields(BlogPost post, AdminBlogPostWriteRequest request, boolean existingPost) {
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
        validateImageSource(request.coverImageUrl(), request.coverImageObjectKey(), "cover image");
        validateImageSource(request.ogImageUrl(), request.ogImageObjectKey(), "OG image");
        post.setExcerpt(contentPolicy.cleanNullable(request.excerpt()));
        post.setContentMarkdown(contentPolicy.cleanNullable(request.contentMarkdown()));
        post.setContentHash(contentPolicy.sha256Hex(request.contentMarkdown()));
        post.setReadingTimeMinutes(contentPolicy.readingTimeMinutes(request.contentMarkdown()));
        post.setCoverImageUrl(contentPolicy.cleanNullable(request.coverImageUrl()));
        post.setCoverImageObjectKey(contentPolicy.cleanNullable(request.coverImageObjectKey()));
        post.setOgImageUrl(contentPolicy.cleanNullable(request.ogImageUrl()));
        post.setOgImageObjectKey(contentPolicy.cleanNullable(request.ogImageObjectKey()));
        post.setVisibility(request.visibility() == null ? BlogVisibility.PUBLIC : request.visibility());
        post.setSeoTitle(contentPolicy.cleanNullable(request.seoTitle()));
        post.setSeoDescription(contentPolicy.cleanNullable(request.seoDescription()));
        post.setCanonicalUrl(validateCanonicalUrl(request.canonicalUrl()));
        post.setCategories(new LinkedHashSet<>(loadCategories(request.categoryIds())));
        post.setTags(new LinkedHashSet<>(loadTags(request.tagIds())));
    }

    private void requireVersion(BlogPost post, UUID postId, Integer expectedVersion) {
        if (!Objects.equals(post.getVersion(), expectedVersion)) {
            throw new VersionConflictException(ErrorCode.BLOG_POST_VERSION_CONFLICT, ErrorCode.BLOG_POST_VERSION_CONFLICT.getMessage(), postId, expectedVersion, post.getVersion());
        }
    }

    private void applyTagFields(BlogTag tag, AdminBlogTagWriteRequest request, String slug) {
        tag.setName(contentPolicy.cleanRequired(request.name(), "Tên tag"));
        tag.setSlug(slug);
        tag.setActive(request.active() == null || request.active());
    }

    private String normalizedTagSlug(String name, String requestedSlug) {
        return contentPolicy.slugify(contentPolicy.cleanNullable(requestedSlug) == null ? name : requestedSlug);
    }

    private void validateImageSource(String directUrl, String objectKey, String fieldName) {
        String cleanedUrl = contentPolicy.cleanNullable(directUrl);
        String cleanedObjectKey = contentPolicy.cleanNullable(objectKey);
        if (cleanedUrl != null && cleanedObjectKey != null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, fieldName + " chỉ được gửi URL hoặc object key");
        }
        if (cleanedUrl != null && !cleanedUrl.startsWith("https://") && !cleanedUrl.startsWith("http://")) {
            throw new BaseException(ErrorCode.BAD_REQUEST, fieldName + " phải là URL http hoặc https");
        }
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

    private void requireRequest(Object request) {
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Request bài blog không được để trống");
        }
    }

    private List<AdminBlogPostCardResponse> mapAdminCards(List<BlogPost> posts) {
        return hydratePosts(posts).stream().map(blogMapper::toAdminCard).toList();
    }

    private List<BlogPost> hydratePosts(List<BlogPost> posts) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = posts.stream().map(BlogPost::getId).distinct().toList();
        var byId = blogPostRepository.findReaderPostsWithAuthorByIdIn(ids).stream()
                .collect(Collectors.toMap(BlogPost::getId, post -> post));
        blogPostRepository.loadCategoriesByPostIdIn(ids);
        blogPostRepository.loadTagsByPostIdIn(ids);
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private void signalTrendingChange(UUID postId) {
        eventPublisher.publishEvent(new BlogTrendingRankingChangedEvent(postId));
    }

    private void signalTaxonomyChange() {
        eventPublisher.publishEvent(new BlogTaxonomyChangedEvent());
    }

    public record CategoryUpsertResult(BlogCategoryResponse response, boolean created) {
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
