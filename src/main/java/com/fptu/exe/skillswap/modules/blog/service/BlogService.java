package com.fptu.exe.skillswap.modules.blog.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.blog.domain.BlogAudienceType;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPost;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPostStatus;
import com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility;
import com.fptu.exe.skillswap.modules.blog.dto.BlogCategoryResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogEngagementState;
import com.fptu.exe.skillswap.modules.blog.dto.BlogFollowResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogPostCardResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogPostDetailResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogTagResponse;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogAuthorCtaClickRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogViewRequest;
import com.fptu.exe.skillswap.modules.blog.domain.BlogBookmark;
import com.fptu.exe.skillswap.modules.blog.domain.BlogCategory;
import com.fptu.exe.skillswap.modules.blog.domain.BlogCategoryFollow;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPostLike;
import com.fptu.exe.skillswap.modules.blog.domain.BlogTag;
import com.fptu.exe.skillswap.modules.blog.domain.BlogTagFollow;
import com.fptu.exe.skillswap.modules.blog.repository.BlogBookmarkRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogCategoryFollowRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogCategoryRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogPostRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogPostLikeRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogTagFollowRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogTagRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.mentor.service.MentorBlogAuthorSummary;
import com.fptu.exe.skillswap.modules.mentor.service.MentorContentAccessService;
import com.fptu.exe.skillswap.modules.system.service.InternalTelemetryService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.cursor.CursorCodec;
import com.fptu.exe.skillswap.shared.cursor.CursorTokenPayload;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BlogService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final String DIRECTION_NEXT = "NEXT";

    private final BlogPostRepository blogPostRepository;
    private final BlogPostLikeRepository blogPostLikeRepository;
    private final BlogBookmarkRepository blogBookmarkRepository;
    private final BlogCategoryFollowRepository blogCategoryFollowRepository;
    private final BlogTagFollowRepository blogTagFollowRepository;
    private final BlogCategoryRepository blogCategoryRepository;
    private final BlogTagRepository blogTagRepository;
    private final BlogMapper blogMapper;
    private final CursorCodec cursorCodec;
    private final BlogContentPolicy contentPolicy;
    private final MentorContentAccessService mentorContentAccessService;
    private final InternalTelemetryService internalTelemetryService;
    private final EntityManager entityManager;

    private final Cache<String, Boolean> viewDedupeCache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(100_000)
            .build();

    private final Cache<String, List<BlogPostCardResponse>> trendingCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(200)
            .build();

    @Transactional(readOnly = true)
    public CursorPageResponse<BlogPostCardResponse> listPosts(UserPrincipal principal,
                                                              String cursor,
                                                              Integer limit,
                                                              UUID categoryId,
                                                              UUID tagId,
                                                              BlogAudienceType audienceType,
                                                              String keyword) {
        int resolvedLimit = resolveLimit(limit);
        String keywordPattern = likePattern(keyword);
        List<BlogVisibility> allowedVisibilities = allowedVisibilities(principal);
        String filterHash = filterHash("blog-posts:public|visibility=" + allowedVisibilities
                + "|categoryId=" + normalize(categoryId)
                + "|tagId=" + normalize(tagId)
                + "|audienceType=" + normalize(audienceType)
                + "|keyword=" + normalizeKeyword(keyword));

        DecodedCursor decodedCursor = decodeCursor(cursor, filterHash);
        List<BlogPost> window = blogPostRepository.findPublicWindow(
                allowedVisibilities,
                categoryId,
                tagId,
                audienceType,
                keywordPattern,
                decodedCursor.sortTime(),
                decodedCursor.postId(),
                resolvedLimit + 1
        );
        boolean hasNext = window.size() > resolvedLimit;
        List<BlogPost> items = hasNext ? window.subList(0, resolvedLimit) : window;
        String nextCursor = hasNext && !items.isEmpty()
                ? encodeCursor(items.get(items.size() - 1).getPublishedAt(), items.get(items.size() - 1).getId(), filterHash)
                : null;
        return CursorPageResponse.<BlogPostCardResponse>builder()
                .items(mapCards(principal, items))
                .nextCursor(nextCursor)
                .prevCursor(null)
                .hasNext(hasNext)
                .hasPrev(false)
                .limit(resolvedLimit)
                .build();
    }

    @Transactional(readOnly = true)
    public List<BlogPostCardResponse> featured(UserPrincipal principal, int limit) {
        List<BlogVisibility> allowed = allowedVisibilities(principal);
        return blogPostRepository.findFeatured(BlogPostStatus.PUBLISHED, DateTimeUtil.now())
                .stream()
                .filter(post -> allowed.contains(post.getVisibility()))
                .limit(Math.min(Math.max(limit, 1), 20))
                .collect(Collectors.collectingAndThen(Collectors.toList(), posts -> mapCards(principal, posts)));
    }

    @Transactional(readOnly = true)
    public List<BlogPostCardResponse> trending(UserPrincipal principal, int limit) {
        int resolvedLimit = Math.min(Math.max(limit, 1), 20);
        List<BlogVisibility> allowed = allowedVisibilities(principal);
        String key = "trending:" + allowed + ":" + resolvedLimit + ":" + userId(principal);
        return trendingCache.get(key, ignored -> {
            List<BlogPost> posts = blogPostRepository.findTrendingCandidates(
                            BlogPostStatus.PUBLISHED,
                            allowed,
                            PageRequest.of(0, Math.max(resolvedLimit * 3, 20)))
                    .stream()
                    .limit(resolvedLimit)
                    .toList();
            return mapCards(principal, posts);
        });
    }

    @Transactional(readOnly = true)
    public List<BlogPostCardResponse> related(UserPrincipal principal, String slug, int limit) {
        BlogPost source = blogPostRepository.findBySlug(slug)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy bài blog"));
        ensureReadable(principal, source);
        Set<UUID> categoryIds = source.getCategories().stream().map(category -> category.getId()).collect(Collectors.toSet());
        Set<UUID> tagIds = source.getTags().stream().map(tag -> tag.getId()).collect(Collectors.toSet());
        List<BlogPost> posts = blogPostRepository.findRelatedCandidates(
                BlogPostStatus.PUBLISHED,
                source.getId(),
                allowedVisibilities(principal),
                source.getVisibility(),
                categoryIds.isEmpty() ? Set.of(new UUID(0L, 0L)) : categoryIds,
                categoryIds.isEmpty(),
                tagIds.isEmpty() ? Set.of(new UUID(0L, 0L)) : tagIds,
                tagIds.isEmpty(),
                source.getAudienceType(),
                PageRequest.of(0, Math.min(Math.max(limit, 1), 12))
        );
        return mapCards(principal, posts);
    }

    @Transactional(readOnly = true)
    public BlogPostDetailResponse getBySlug(UserPrincipal principal, String slug) {
        BlogPost post = blogPostRepository.findBySlug(slug)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy bài blog"));
        ensureReadable(principal, post);
        return blogMapper.toDetail(post, engagementState(principal, post.getId()), authorSummary(post));
    }

    @Transactional
    public BlogPostDetailResponse like(UserPrincipal principal, UUID postId) {
        UUID userId = requireAuthenticated(principal);
        BlogPost post = loadReadablePost(principal, postId);
        if (!blogPostLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            try {
                blogPostLikeRepository.save(BlogPostLike.builder()
                        .post(entityManager.getReference(BlogPost.class, postId))
                        .user(entityManager.getReference(User.class, userId))
                        .build());
                blogPostRepository.incrementLikeCount(postId);
                internalTelemetryService.record("BLOG_LIKE", userId, "BLOG_POST", postId, Map.of("slug", post.getSlug()));
            } catch (DataIntegrityViolationException ignored) {
                // Idempotent behavior for concurrent like requests.
            }
        }
        return blogMapper.toDetail(loadPost(postId), engagementState(principal, postId), authorSummary(post));
    }

    @Transactional
    public BlogPostDetailResponse unlike(UserPrincipal principal, UUID postId) {
        UUID userId = requireAuthenticated(principal);
        BlogPost post = loadReadablePost(principal, postId);
        if (blogPostLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            blogPostLikeRepository.deleteByPostIdAndUserId(postId, userId);
            blogPostRepository.decrementLikeCount(postId);
        }
        return blogMapper.toDetail(loadPost(postId), engagementState(principal, postId), authorSummary(post));
    }

    @Transactional
    public BlogPostDetailResponse bookmark(UserPrincipal principal, UUID postId) {
        UUID userId = requireAuthenticated(principal);
        BlogPost post = loadReadablePost(principal, postId);
        if (!blogBookmarkRepository.existsByPostIdAndUserId(postId, userId)) {
            try {
                blogBookmarkRepository.save(BlogBookmark.builder()
                        .post(entityManager.getReference(BlogPost.class, postId))
                        .user(entityManager.getReference(User.class, userId))
                        .build());
                blogPostRepository.incrementBookmarkCount(postId);
                internalTelemetryService.record("BLOG_BOOKMARK", userId, "BLOG_POST", postId, Map.of("slug", post.getSlug()));
            } catch (DataIntegrityViolationException ignored) {
                // Idempotent behavior for concurrent bookmark requests.
            }
        }
        return blogMapper.toDetail(loadPost(postId), engagementState(principal, postId), authorSummary(post));
    }

    @Transactional
    public BlogPostDetailResponse unbookmark(UserPrincipal principal, UUID postId) {
        UUID userId = requireAuthenticated(principal);
        BlogPost post = loadReadablePost(principal, postId);
        if (blogBookmarkRepository.existsByPostIdAndUserId(postId, userId)) {
            blogBookmarkRepository.deleteByPostIdAndUserId(postId, userId);
            blogPostRepository.decrementBookmarkCount(postId);
        }
        return blogMapper.toDetail(loadPost(postId), engagementState(principal, postId), authorSummary(post));
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<BlogPostCardResponse> myBookmarks(UserPrincipal principal, String cursor, Integer limit) {
        UUID userId = requireAuthenticated(principal);
        int resolvedLimit = resolveLimit(limit);
        String filterHash = filterHash("blog-bookmarks|userId=" + userId);
        DecodedCursor decodedCursor = decodeCursor(cursor, filterHash);
        List<BlogBookmark> window = blogBookmarkRepository.findBookmarkWindow(
                userId,
                decodedCursor.sortTime(),
                decodedCursor.postId(),
                resolvedLimit + 1
        );
        boolean hasNext = window.size() > resolvedLimit;
        List<BlogBookmark> items = hasNext ? window.subList(0, resolvedLimit) : window;
        List<BlogPost> posts = items.stream().map(BlogBookmark::getPost).toList();
        String nextCursor = hasNext && !items.isEmpty()
                ? encodeCursor(items.get(items.size() - 1).getCreatedAt(), items.get(items.size() - 1).getPost().getId(), filterHash)
                : null;
        return CursorPageResponse.<BlogPostCardResponse>builder()
                .items(mapCards(principal, posts))
                .nextCursor(nextCursor)
                .prevCursor(null)
                .hasNext(hasNext)
                .hasPrev(false)
                .limit(resolvedLimit)
                .build();
    }

    @Transactional
    public BlogFollowResponse followCategory(UserPrincipal principal, UUID categoryId) {
        UUID userId = requireAuthenticated(principal);
        BlogCategory category = loadActiveCategory(categoryId);
        if (!blogCategoryFollowRepository.existsByUserIdAndCategoryId(userId, categoryId)) {
            try {
                blogCategoryFollowRepository.save(BlogCategoryFollow.builder()
                        .user(entityManager.getReference(User.class, userId))
                        .category(category)
                        .build());
                internalTelemetryService.record("BLOG_CATEGORY_FOLLOW", userId, "BLOG_CATEGORY", categoryId, Map.of("slug", category.getSlug()));
            } catch (DataIntegrityViolationException ignored) {
                // Idempotent behavior for concurrent follow requests.
            }
        }
        return myFollows(principal);
    }

    @Transactional
    public BlogFollowResponse unfollowCategory(UserPrincipal principal, UUID categoryId) {
        UUID userId = requireAuthenticated(principal);
        blogCategoryFollowRepository.deleteByUserIdAndCategoryId(userId, categoryId);
        return myFollows(principal);
    }

    @Transactional
    public BlogFollowResponse followTag(UserPrincipal principal, UUID tagId) {
        UUID userId = requireAuthenticated(principal);
        BlogTag tag = loadActiveTag(tagId);
        if (!blogTagFollowRepository.existsByUserIdAndTagId(userId, tagId)) {
            try {
                blogTagFollowRepository.save(BlogTagFollow.builder()
                        .user(entityManager.getReference(User.class, userId))
                        .tag(tag)
                        .build());
                internalTelemetryService.record("BLOG_TAG_FOLLOW", userId, "BLOG_TAG", tagId, Map.of("slug", tag.getSlug()));
            } catch (DataIntegrityViolationException ignored) {
                // Idempotent behavior for concurrent follow requests.
            }
        }
        return myFollows(principal);
    }

    @Transactional
    public BlogFollowResponse unfollowTag(UserPrincipal principal, UUID tagId) {
        UUID userId = requireAuthenticated(principal);
        blogTagFollowRepository.deleteByUserIdAndTagId(userId, tagId);
        return myFollows(principal);
    }

    @Transactional(readOnly = true)
    public BlogFollowResponse myFollows(UserPrincipal principal) {
        UUID userId = requireAuthenticated(principal);
        List<BlogCategoryResponse> categories = blogCategoryFollowRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(follow -> blogMapper.toCategory(follow.getCategory()))
                .toList();
        List<BlogTagResponse> tags = blogTagFollowRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(follow -> blogMapper.toTag(follow.getTag()))
                .toList();
        return new BlogFollowResponse(categories, tags);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<BlogPostCardResponse> personalizedFeed(UserPrincipal principal, String cursor, Integer limit) {
        UUID userId = requireAuthenticated(principal);
        int resolvedLimit = resolveLimit(limit);
        Set<UUID> categoryIds = blogCategoryFollowRepository.findCategoryIdsByUserId(userId);
        Set<UUID> tagIds = blogTagFollowRepository.findTagIdsByUserId(userId);
        String filterHash = filterHash("blog-feed|userId=" + userId + "|categories=" + categoryIds + "|tags=" + tagIds);
        DecodedCursor decodedCursor = decodeCursor(cursor, filterHash);
        List<BlogPost> window;
        if (categoryIds.isEmpty() && tagIds.isEmpty()) {
            window = blogPostRepository.findPublicWindow(
                    allowedVisibilities(principal),
                    null,
                    null,
                    null,
                    null,
                    decodedCursor.sortTime(),
                    decodedCursor.postId(),
                    resolvedLimit + 1
            );
        } else {
            window = blogPostRepository.findPersonalizedFeedWindow(
                    allowedVisibilities(principal),
                    categoryIds,
                    tagIds,
                    decodedCursor.sortTime(),
                    decodedCursor.postId(),
                    resolvedLimit + 1
            );
        }
        boolean hasNext = window.size() > resolvedLimit;
        List<BlogPost> items = hasNext ? window.subList(0, resolvedLimit) : window;
        String nextCursor = hasNext && !items.isEmpty()
                ? encodeCursor(items.get(items.size() - 1).getPublishedAt(), items.get(items.size() - 1).getId(), filterHash)
                : null;
        internalTelemetryService.record("BLOG_FEED_VIEW", userId, "USER", userId, Map.of(
                "categoryFollowCount", categoryIds.size(),
                "tagFollowCount", tagIds.size(),
                "resultCount", items.size()
        ));
        return CursorPageResponse.<BlogPostCardResponse>builder()
                .items(mapCards(principal, items))
                .nextCursor(nextCursor)
                .prevCursor(null)
                .hasNext(hasNext)
                .hasPrev(false)
                .limit(resolvedLimit)
                .build();
    }

    @Transactional(readOnly = true)
    public List<BlogPostCardResponse> recommendations(UserPrincipal principal, String slug, int limit) {
        return related(principal, slug, limit);
    }

    @Transactional
    public void recordView(UserPrincipal principal, UUID postId, BlogViewRequest request) {
        recordView(principal, postId, request, null);
    }

    @Transactional
    public void recordView(UserPrincipal principal, UUID postId, BlogViewRequest request, String serverFingerprint) {
        BlogPost post = blogPostRepository.findById(postId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy bài blog"));
        ensureReadable(principal, post);
        String dedupeKey = "BLOG_VIEW:" + postId + ":" + viewerKey(principal, request == null ? null : request.sessionId(), serverFingerprint);
        if (viewDedupeCache.asMap().putIfAbsent(dedupeKey, Boolean.TRUE) == null) {
            blogPostRepository.incrementViewCount(postId);
            internalTelemetryService.record("BLOG_VIEW", userId(principal), "BLOG_POST", postId, Map.of(
                    "slug", post.getSlug(),
                    "sessionIdPresent", request != null && contentPolicy.hasText(request.sessionId())
            ));
        }
    }

    @Transactional
    public void recordAuthorCtaClick(UserPrincipal principal, UUID postId, BlogAuthorCtaClickRequest request) {
        BlogPost post = blogPostRepository.findById(postId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy bài blog"));
        ensureReadable(principal, post);
        internalTelemetryService.record("BLOG_AUTHOR_CTA_CLICK", userId(principal), "BLOG_POST", postId, Map.of(
                "authorUserId", post.getAuthorUser().getId().toString(),
                "ctaType", request == null || !contentPolicy.hasText(request.ctaType()) ? "AUTHOR_PROFILE" : request.ctaType().trim(),
                "sessionIdPresent", request != null && contentPolicy.hasText(request.sessionId())
        ));
    }

    @Transactional
    public void recordBookingStarted(UserPrincipal principal, UUID postId, BlogAuthorCtaClickRequest request) {
        BlogPost post = blogPostRepository.findById(postId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy bài blog"));
        ensureReadable(principal, post);
        internalTelemetryService.record("BLOG_BOOKING_STARTED", userId(principal), "BLOG_POST", postId, Map.of(
                "authorUserId", post.getAuthorUser().getId().toString(),
                "ctaType", request == null || !contentPolicy.hasText(request.ctaType()) ? "BOOK_SESSION" : request.ctaType().trim(),
                "sessionIdPresent", request != null && contentPolicy.hasText(request.sessionId())
        ));
    }

    @Transactional
    public void recordNotificationClick(UserPrincipal principal, UUID postId, BlogAuthorCtaClickRequest request) {
        UUID userId = requireAuthenticated(principal);
        BlogPost post = loadReadablePost(principal, postId);
        internalTelemetryService.record("BLOG_NOTIFICATION_CLICK", userId, "BLOG_POST", postId, Map.of(
                "slug", post.getSlug(),
                "sessionIdPresent", request != null && contentPolicy.hasText(request.sessionId())
        ));
    }

    @Transactional
    public void recordRecommendationClick(UserPrincipal principal, UUID postId, BlogAuthorCtaClickRequest request) {
        UUID userId = requireAuthenticated(principal);
        BlogPost post = loadReadablePost(principal, postId);
        internalTelemetryService.record("BLOG_RECOMMENDATION_CLICK", userId, "BLOG_POST", postId, Map.of(
                "slug", post.getSlug(),
                "sessionIdPresent", request != null && contentPolicy.hasText(request.sessionId())
        ));
    }

    @Transactional(readOnly = true)
    public List<BlogCategoryResponse> categories() {
        return blogCategoryRepository.findByActiveTrueOrderByDisplayOrderAscNameAsc()
                .stream()
                .map(blogMapper::toCategory)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BlogTagResponse> tags() {
        return blogTagRepository.findByActiveTrueOrderByNameAsc()
                .stream()
                .map(blogMapper::toTag)
                .toList();
    }

    private void ensureReadable(UserPrincipal principal, BlogPost post) {
        if (post.getStatus() != BlogPostStatus.PUBLISHED || post.getPublishedAt() == null) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy bài blog");
        }
        if (!allowedVisibilities(principal).contains(post.getVisibility())) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy bài blog");
        }
    }

    private BlogPost loadReadablePost(UserPrincipal principal, UUID postId) {
        BlogPost post = blogPostRepository.findById(postId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy bài blog"));
        ensureReadable(principal, post);
        return post;
    }

    private BlogPost loadPost(UUID postId) {
        return blogPostRepository.findById(postId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy bài blog"));
    }

    private BlogCategory loadActiveCategory(UUID categoryId) {
        if (categoryId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "categoryId không được để trống");
        }
        return blogCategoryRepository.findById(categoryId)
                .filter(BlogCategory::isActive)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy blog category đang active"));
    }

    private BlogTag loadActiveTag(UUID tagId) {
        if (tagId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "tagId không được để trống");
        }
        return blogTagRepository.findById(tagId)
                .filter(BlogTag::isActive)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy blog tag đang active"));
    }

    private List<BlogPostCardResponse> mapCards(UserPrincipal principal, List<BlogPost> posts) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        Map<UUID, BlogEngagementState> engagement = engagementStates(principal, posts.stream().map(BlogPost::getId).toList());
        Map<UUID, MentorBlogAuthorSummary> authorSummaries = mentorContentAccessService.getBlogAuthorSummaries(
                posts.stream().map(post -> post.getAuthorUser().getId()).collect(Collectors.toSet())
        );
        return posts.stream()
                .map(post -> blogMapper.toCard(
                        post,
                        engagement.getOrDefault(post.getId(), BlogEngagementState.empty()),
                        authorSummaries.get(post.getAuthorUser().getId())))
                .toList();
    }

    private BlogEngagementState engagementState(UserPrincipal principal, UUID postId) {
        if (principal == null || postId == null) {
            return BlogEngagementState.empty();
        }
        UUID userId = principal.getPublicId();
        return new BlogEngagementState(
                blogPostLikeRepository.existsByPostIdAndUserId(postId, userId),
                blogBookmarkRepository.existsByPostIdAndUserId(postId, userId)
        );
    }

    private Map<UUID, BlogEngagementState> engagementStates(UserPrincipal principal, Collection<UUID> postIds) {
        if (principal == null || postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        UUID userId = principal.getPublicId();
        Set<UUID> liked = blogPostLikeRepository.findLikedPostIds(userId, postIds);
        Set<UUID> bookmarked = blogBookmarkRepository.findBookmarkedPostIds(userId, postIds);
        return postIds.stream()
                .distinct()
                .collect(Collectors.toMap(Function.identity(), postId -> new BlogEngagementState(liked.contains(postId), bookmarked.contains(postId))));
    }

    private MentorBlogAuthorSummary authorSummary(BlogPost post) {
        if (post == null || post.getAuthorUser() == null) {
            return null;
        }
        return mentorContentAccessService.getBlogAuthorSummaries(Set.of(post.getAuthorUser().getId()))
                .get(post.getAuthorUser().getId());
    }

    private List<BlogVisibility> allowedVisibilities(UserPrincipal principal) {
        List<BlogVisibility> allowed = new ArrayList<>();
        allowed.add(BlogVisibility.PUBLIC);
        if (principal != null) {
            allowed.add(BlogVisibility.MEMBERS_ONLY);
            if (isMentorPrincipal(principal)
                    && mentorContentAccessService.canAccessMentorOnlyContent(principal.getPublicId())) {
                allowed.add(BlogVisibility.MENTOR_ONLY);
            }
        }
        return allowed;
    }

    private boolean isMentorPrincipal(UserPrincipal principal) {
        return principal.getRoles() != null && principal.getRoles().contains(RoleCode.MENTOR);
    }

    private UUID userId(UserPrincipal principal) {
        return principal == null ? null : principal.getPublicId();
    }

    private UUID requireAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        return principal.getPublicId();
    }

    private String viewerKey(UserPrincipal principal, String sessionId, String serverFingerprint) {
        if (principal != null) {
            return "u:" + principal.getPublicId();
        }
        if (contentPolicy.hasText(serverFingerprint)) {
            return "a:" + sha256(serverFingerprint);
        }
        if (contentPolicy.hasText(sessionId)) {
            return "s:" + sha256(sessionId.trim());
        }
        return "anon";
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String likePattern(String keyword) {
        if (!contentPolicy.hasText(keyword)) {
            return null;
        }
        return "%" + keyword.trim().toLowerCase() + "%";
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
        return sha256(value);
    }

    private String sha256(String value) {
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
