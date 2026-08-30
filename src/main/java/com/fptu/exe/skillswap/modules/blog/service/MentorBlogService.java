package com.fptu.exe.skillswap.modules.blog.service;

import com.fptu.exe.skillswap.modules.blog.domain.BlogAuthorType;
import com.fptu.exe.skillswap.modules.blog.domain.BlogCategory;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPost;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPostStatus;
import com.fptu.exe.skillswap.modules.blog.domain.BlogTag;
import com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility;
import com.fptu.exe.skillswap.modules.blog.dto.MentorBlogPostDetailResponse;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogExpectedVersionRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.MentorBlogPostCreateRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.MentorBlogPostUpdateRequest;
import com.fptu.exe.skillswap.modules.blog.event.BlogPostPublishedEvent;
import com.fptu.exe.skillswap.modules.blog.event.BlogTrendingRankingChangedEvent;
import com.fptu.exe.skillswap.modules.blog.repository.BlogCategoryRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogPostRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogTagRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.port.MentorQueryPort;
import com.fptu.exe.skillswap.modules.filestorage.service.PublicAssetUploadService;
import com.fptu.exe.skillswap.modules.filestorage.dto.request.PublicAssetUploadIntentRequest;
import com.fptu.exe.skillswap.modules.filestorage.dto.response.PublicAssetResponse;
import com.fptu.exe.skillswap.modules.filestorage.dto.response.PublicAssetUploadIntentResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.VersionConflictException;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import com.fptu.exe.skillswap.shared.util.UuidUtil;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MentorBlogService {
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile("!\\[[^]]*]\\(([^\\s)]+)(?:\\s+\\\"[^\\\"]*\\\")?\\)");
    private final BlogPostRepository postRepository;
    private final BlogCategoryRepository categoryRepository;
    private final BlogTagRepository tagRepository;
    private final MentorQueryPort mentorQueryPort;
    private final BlogContentPolicy contentPolicy;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;
    private final PublicAssetUploadService publicAssetUploadService;

    @Transactional(readOnly = true)
    public List<MentorBlogPostDetailResponse> list(UUID mentorId) {
        return postRepository.findAdminWindow(null, mentorId, null, null, null, null, null, 50).stream()
                .filter(post -> post.getAuthorType() == BlogAuthorType.MENTOR)
                .map(this::toResponse).toList();
    }

    @Transactional
    public PublicAssetUploadIntentResponse createImageUploadIntent(UUID mentorId, PublicAssetUploadIntentRequest request) {
        requireEligibleAuthor(mentorId);
        return publicAssetUploadService.createBlogImageIntent(mentorId, request);
    }

    @Transactional
    public PublicAssetResponse confirmImageUpload(UUID mentorId, UUID intentId) {
        requireEligibleAuthor(mentorId);
        return publicAssetUploadService.confirmBlogImage(mentorId, intentId);
    }

    @Transactional(readOnly = true)
    public MentorBlogPostDetailResponse get(UUID mentorId, UUID postId) { return toResponse(loadOwned(mentorId, postId)); }

    @Transactional
    public MentorBlogPostDetailResponse create(UUID mentorId, MentorBlogPostCreateRequest request) {
        requireEligibleAuthor(mentorId);
        BlogPost post = BlogPost.builder()
                .authorUser(entityManager.getReference(com.fptu.exe.skillswap.modules.identity.domain.User.class, mentorId))
                .authorType(BlogAuthorType.MENTOR)
                .title(contentPolicy.cleanRequired(request.title(), "Tiêu đề bài blog"))
                .slug(uniqueSlug(request.title(), null))
                .status(BlogPostStatus.DRAFT).build();
        apply(post, mentorId, request.title(), request.excerpt(), request.contentMarkdown(), request.coverAssetId(), request.ogAssetId(),
                request.visibility(), request.categoryIds(), request.tagIds(), request.entitledServiceIds());
        return toResponse(postRepository.save(post));
    }

    @Transactional
    public MentorBlogPostDetailResponse update(UUID mentorId, UUID postId, MentorBlogPostUpdateRequest request) {
        requireEligibleAuthor(mentorId);
        BlogPost post = loadOwned(mentorId, postId);
        requireVersion(post, postId, request.expectedVersion());
        String title = contentPolicy.cleanRequired(request.title(), "Tiêu đề bài blog");
        if (!post.isSlugLocked()) post.setSlug(uniqueSlug(title, post.getId()));
        apply(post, mentorId, title, request.excerpt(), request.contentMarkdown(), request.coverAssetId(), request.ogAssetId(),
                request.visibility(), request.categoryIds(), request.tagIds(), request.entitledServiceIds());
        return toResponse(postRepository.save(post));
    }

    @Transactional
    public MentorBlogPostDetailResponse publish(UUID mentorId, UUID postId, BlogExpectedVersionRequest request) {
        requireEligibleAuthor(mentorId);
        BlogPost post = loadOwned(mentorId, postId);
        requireVersion(post, postId, request.expectedVersion());
        if (!contentPolicy.hasText(post.getContentMarkdown())) throw new BaseException(ErrorCode.BAD_REQUEST, "Không thể publish bài chưa có nội dung");
        if (post.getVisibility() == BlogVisibility.BOOKED_MEMBERS && post.getEntitledServices().isEmpty()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Bài premium phải gắn ít nhất một dịch vụ");
        }
        LocalDateTime now = DateTimeUtil.now();
        if (post.getPublishedAt() == null) post.setPublishedAt(now);
        post.setLastPublishedAt(now); post.setStatus(BlogPostStatus.PUBLISHED); post.setSlugLocked(true);
        BlogPost saved = postRepository.save(post);
        eventPublisher.publishEvent(new BlogPostPublishedEvent(UuidUtil.generateUuidV7(), saved.getId(), saved.getSlug(), saved.getTitle(),
                mentorId, saved.getAuthorUser().getFullName(), saved.getVisibility(),
                saved.getCategories().stream().map(BlogCategory::getId).collect(java.util.stream.Collectors.toSet()),
                saved.getEntitledServices().stream().map(MentorService::getId).collect(java.util.stream.Collectors.toSet()), now));
        eventPublisher.publishEvent(new BlogTrendingRankingChangedEvent(saved.getId()));
        return toResponse(saved);
    }

    @Transactional
    public MentorBlogPostDetailResponse archive(UUID mentorId, UUID postId, BlogExpectedVersionRequest request) {
        BlogPost post = loadOwned(mentorId, postId);
        requireVersion(post, postId, request.expectedVersion());
        post.setStatus(BlogPostStatus.ARCHIVED); post.setFeatured(false); post.setFeaturedOrder(null); post.setFeaturedUntil(null);
        BlogPost saved = postRepository.save(post);
        eventPublisher.publishEvent(new BlogTrendingRankingChangedEvent(saved.getId()));
        return toResponse(saved);
    }

    private void apply(BlogPost post, UUID mentorId, String title, String excerpt, String markdown, UUID coverAssetId, UUID ogAssetId,
                       BlogVisibility visibility, List<UUID> categoryIds, List<UUID> tagIds, List<UUID> serviceIds) {
        contentPolicy.validateMarkdown(markdown);
        validateOwnedInlineImages(mentorId, markdown);
        post.setTitle(contentPolicy.cleanRequired(title, "Tiêu đề bài blog"));
        post.setExcerpt(contentPolicy.cleanNullable(excerpt)); post.setContentMarkdown(contentPolicy.cleanNullable(markdown));
        post.setContentHash(contentPolicy.sha256Hex(markdown)); post.setReadingTimeMinutes(contentPolicy.readingTimeMinutes(markdown));
        post.setCoverImageUrl(resolveAssetUrl(mentorId, coverAssetId)); post.setCoverImageObjectKey(null);
        post.setOgImageUrl(resolveAssetUrl(mentorId, ogAssetId)); post.setOgImageObjectKey(null);
        post.setVisibility(visibility == null ? BlogVisibility.PUBLIC : visibility);
        post.setCategories(new LinkedHashSet<>(loadCategories(categoryIds))); post.setTags(new LinkedHashSet<>(loadTags(tagIds)));
        post.setEntitledServices(new LinkedHashSet<>(loadOwnedServices(mentorId, serviceIds)));
    }

    private void requireEligibleAuthor(UUID mentorId) {
        MentorProfile profile = mentorProfileRepository.findWithUserByUserId(mentorId)
                .orElseThrow(() -> new BaseException(ErrorCode.ACCESS_DENIED, "Chỉ mentor được xác minh mới có thể viết blog"));
        if (profile.getStatus() != MentorStatus.ACTIVE || profile.getVerifiedAt() == null) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Mentor hiện chưa đủ điều kiện xuất bản blog");
        }
    }

    private BlogPost loadOwned(UUID mentorId, UUID postId) {
        return postRepository.findById(postId).filter(post -> post.getAuthorType() == BlogAuthorType.MENTOR && post.getAuthorUser().getId().equals(mentorId))
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy bài blog"));
    }

    private void requireVersion(BlogPost post, UUID postId, Integer expected) {
        if (!java.util.Objects.equals(post.getVersion(), expected)) throw new VersionConflictException(ErrorCode.BLOG_POST_VERSION_CONFLICT,
                ErrorCode.BLOG_POST_VERSION_CONFLICT.getMessage(), postId, expected, post.getVersion());
    }

    private List<BlogCategory> loadCategories(List<UUID> ids) { return ids == null || ids.isEmpty() ? List.of() : categoryRepository.findByIdInAndActiveTrue(new LinkedHashSet<>(ids)); }
    private List<BlogTag> loadTags(List<UUID> ids) { return ids == null || ids.isEmpty() ? List.of() : tagRepository.findByIdInAndActiveTrue(new LinkedHashSet<>(ids)); }
    private List<MentorService> loadOwnedServices(UUID mentorId, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<MentorService> services = ids.stream().distinct().map(id -> mentorServiceRepository.findByIdAndMentorProfileUserId(id, mentorId)
                .orElseThrow(() -> new BaseException(ErrorCode.BAD_REQUEST, "Dịch vụ entitlement không thuộc mentor"))).toList();
        return services;
    }
    private String uniqueSlug(String source, UUID currentId) {
        String base = contentPolicy.slugify(source); String value = base; int suffix = 2;
        while (currentId == null ? postRepository.existsBySlug(value) : postRepository.existsBySlugAndIdNot(value, currentId)) value = base + "-" + suffix++;
        return value;
    }
    private String resolveAssetUrl(UUID ownerId, UUID assetId) {
        return assetId == null ? null : publicAssetUploadService.requireOwnedBlogImage(ownerId, assetId).getPublicUrl();
    }

    private void validateOwnedInlineImages(UUID mentorId, String markdown) {
        if (markdown == null || markdown.isBlank()) return;
        Matcher matcher = MARKDOWN_IMAGE.matcher(markdown);
        while (matcher.find()) publicAssetUploadService.requireOwnedBlogImageUrl(mentorId, matcher.group(1));
    }
    private MentorBlogPostDetailResponse toResponse(BlogPost post) {
        return new MentorBlogPostDetailResponse(post.getId(), post.getTitle(), post.getSlug(), post.isSlugLocked(), post.getExcerpt(), post.getContentMarkdown(),
                post.getCoverImageUrl(), post.getOgImageUrl(), post.getVisibility(), post.getStatus(),
                post.getCategories().stream().map(c -> new com.fptu.exe.skillswap.modules.blog.dto.BlogCategoryResponse(c.getId(), c.getCode(), c.getName(), c.getSlug(), c.getDescription(), c.isActive(), c.getDisplayOrder())).toList(),
                post.getTags().stream().map(t -> new com.fptu.exe.skillswap.modules.blog.dto.BlogTagResponse(t.getId(), t.getName(), t.getSlug(), t.isActive())).toList(),
                post.getEntitledServices().stream().map(MentorService::getId).toList(), post.isFeatured(), post.getPublishedAt(), post.getCreatedAt(), post.getUpdatedAt(), post.getVersion());
    }
}
