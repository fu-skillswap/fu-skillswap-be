package com.fptu.exe.skillswap.modules.blog.service;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPost;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPostStatus;
import com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility;
import com.fptu.exe.skillswap.modules.blog.dto.BlogCategoryResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogEngagementState;
import com.fptu.exe.skillswap.modules.blog.dto.BlogFollowResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogEngagementMutationResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogPostReaderCardResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogPostReaderDetailResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogTagResponse;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogAuthorCtaClickRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogViewRequest;
import com.fptu.exe.skillswap.modules.blog.domain.BlogBookmark;
import com.fptu.exe.skillswap.modules.blog.domain.BlogCategory;
import com.fptu.exe.skillswap.modules.blog.domain.BlogCategoryFollow;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPostLike;
import com.fptu.exe.skillswap.modules.blog.domain.BlogTag;
import com.fptu.exe.skillswap.modules.blog.event.BlogTrendingRankingChangedEvent;
import com.fptu.exe.skillswap.modules.blog.repository.BlogBookmarkRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogCategoryFollowRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogCategoryRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogPostRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogPostLikeRepository;
import com.fptu.exe.skillswap.modules.blog.domain.BlogMentorFollow;
import com.fptu.exe.skillswap.modules.blog.repository.BlogMentorFollowRepository;
import com.fptu.exe.skillswap.modules.booking.service.BookingEligibilityPolicy;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.blog.repository.BlogTagRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.mentor.port.dto.MentorBlogAuthorSummary;
import com.fptu.exe.skillswap.modules.mentor.port.MentorQueryPort;
import com.fptu.exe.skillswap.modules.system.port.TelemetryPort;
import com.fptu.exe.skillswap.shared.cursor.CursorCodec;
import com.fptu.exe.skillswap.shared.cursor.CursorTokenPayload;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BlogService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final String DIRECTION_NEXT = "NEXT";
    private static final int TRENDING_CANDIDATE_LIMIT = 60;
    private static final int MAX_FOLLOWED_CATEGORIES = 20;
    private static final int MAX_FOLLOWED_MENTORS = 20;

    private final BlogPostRepository blogPostRepository;
    private final BlogPostLikeRepository blogPostLikeRepository;
    private final BlogBookmarkRepository blogBookmarkRepository;
    private final BlogCategoryFollowRepository blogCategoryFollowRepository;
    private final BlogMentorFollowRepository blogMentorFollowRepository;
    private final BlogCategoryRepository blogCategoryRepository;
    private final BlogTagRepository blogTagRepository;
    private final BlogMapper blogMapper;
    private final CursorCodec cursorCodec;
    private final BlogContentPolicy contentPolicy;
    private final MentorQueryPort mentorQueryPort;
    private final TelemetryPort internalTelemetryService;
    private final EntityManager entityManager;
    private final BlogTrendingCache trendingCache;
    private final ApplicationEventPublisher eventPublisher;
    private final BookingEligibilityPolicy bookingEligibilityPolicy;
    

    @Transactional(readOnly = true)
    public CursorPageResponse<BlogPostReaderCardResponse> listPosts(UserPrincipal principal,
                                                              String cursor,
                                                              Integer limit,
                                                              UUID categoryId,
                                                              UUID tagId,
                                                              String keyword) {
        int resolvedLimit = resolveLimit(limit);
        String keywordPattern = likePattern(keyword);
        List<BlogVisibility> allowedVisibilities = allowedVisibilities(principal);
        String filterHash = filterHash("blog-posts:public|visibility=" + allowedVisibilities
                + "|categoryId=" + normalize(categoryId)
                + "|tagId=" + normalize(tagId)
                + "|keyword=" + normalizeKeyword(keyword));

        DecodedCursor decodedCursor = decodeCursor(cursor, filterHash);
        List<BlogPost> window = blogPostRepository.findPublicWindow(
                allowedVisibilities,
                categoryId,
                tagId,
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
        return CursorPageResponse.<BlogPostReaderCardResponse>builder()
                .items(mapReaderCards(principal, items))
                .nextCursor(nextCursor)
                .prevCursor(null)
                .hasNext(hasNext)
                .hasPrev(false)
                .limit(resolvedLimit)
                .build();
    }

    @Transactional(readOnly = true)
    public List<BlogPostReaderCardResponse> featured(UserPrincipal principal, int limit) {
        List<BlogVisibility> allowed = allowedVisibilities(principal);
        return blogPostRepository.findFeatured(BlogPostStatus.PUBLISHED, DateTimeUtil.now())
                .stream()
                .filter(post -> allowed.contains(post.getVisibility()))
                .limit(Math.min(Math.max(limit, 1), 20))
                .collect(Collectors.collectingAndThen(Collectors.toList(), posts -> mapReaderCards(principal, posts)));
    }

    @Transactional(readOnly = true)
    public List<BlogPostReaderCardResponse> trending(UserPrincipal principal, int limit) {
        int resolvedLimit = Math.min(Math.max(limit, 1), 20);
        BlogTrendingSegment segment = resolveTrendingSegment(principal);
        List<UUID> rankedIds = trendingCache.get(segment, ignored -> blogPostRepository.findTrendingCandidateIds(
                BlogPostStatus.PUBLISHED,
                allowedVisibilitiesForSegment(segment),
                PageRequest.of(0, TRENDING_CANDIDATE_LIMIT)
        ));
        return mapHydratedReaderCards(principal, hydrateReaderPostsByIds(rankedIds).stream()
                .limit(resolvedLimit)
                .toList());
    }

    @Transactional(readOnly = true)
    public List<BlogPostReaderCardResponse> related(UserPrincipal principal, String slug, int limit) {
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
                PageRequest.of(0, Math.min(Math.max(limit, 1), 12))
        );
        return mapReaderCards(principal, posts);
    }

    @Transactional(readOnly = true)
    public BlogPostReaderDetailResponse getBySlug(UserPrincipal principal, String slug) {
        BlogPost post = blogPostRepository.findBySlug(slug)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy bài blog"));
        ensureReadable(principal, post);
        return blogMapper.toReaderDetail(post, engagementState(principal, post.getId()), authorSummary(post));
    }

    @Transactional
    public BlogEngagementMutationResponse like(UserPrincipal principal, UUID postId) {
        UUID userId = requireAuthenticated(principal);
        BlogPost post = loadReadablePostForEngagement(principal, postId);
        if (!blogPostLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            try {
                blogPostLikeRepository.save(BlogPostLike.builder()
                        .post(entityManager.getReference(BlogPost.class, postId))
                        .user(entityManager.getReference(User.class, userId))
                        .build());
                blogPostRepository.incrementLikeCount(postId);
                internalTelemetryService.record("BLOG_LIKE", userId, "BLOG_POST", postId, Map.of("slug", post.getSlug()));
                signalTrendingChange(postId);
            } catch (DataIntegrityViolationException ignored) {
                // Idempotent behavior for concurrent like requests.
            }
        }
        return engagementMutationResponse(principal, postId);
    }

    @Transactional
    public BlogEngagementMutationResponse unlike(UserPrincipal principal, UUID postId) {
        UUID userId = requireAuthenticated(principal);
        BlogPost post = loadReadablePostForEngagement(principal, postId);
        if (blogPostLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            blogPostLikeRepository.deleteByPostIdAndUserId(postId, userId);
            blogPostRepository.decrementLikeCount(postId);
            signalTrendingChange(postId);
        }
        return engagementMutationResponse(principal, postId);
    }

    @Transactional
    public BlogEngagementMutationResponse bookmark(UserPrincipal principal, UUID postId) {
        UUID userId = requireAuthenticated(principal);
        BlogPost post = loadReadablePostForEngagement(principal, postId);
        if (!blogBookmarkRepository.existsByPostIdAndUserId(postId, userId)) {
            try {
                blogBookmarkRepository.save(BlogBookmark.builder()
                        .post(entityManager.getReference(BlogPost.class, postId))
                        .user(entityManager.getReference(User.class, userId))
                        .build());
                blogPostRepository.incrementBookmarkCount(postId);
                internalTelemetryService.record("BLOG_BOOKMARK", userId, "BLOG_POST", postId, Map.of("slug", post.getSlug()));
                signalTrendingChange(postId);
            } catch (DataIntegrityViolationException ignored) {
                // Idempotent behavior for concurrent bookmark requests.
            }
        }
        return engagementMutationResponse(principal, postId);
    }

    @Transactional
    public BlogEngagementMutationResponse unbookmark(UserPrincipal principal, UUID postId) {
        UUID userId = requireAuthenticated(principal);
        BlogPost post = loadReadablePostForEngagement(principal, postId);
        if (blogBookmarkRepository.existsByPostIdAndUserId(postId, userId)) {
            blogBookmarkRepository.deleteByPostIdAndUserId(postId, userId);
            blogPostRepository.decrementBookmarkCount(postId);
            signalTrendingChange(postId);
        }
        return engagementMutationResponse(principal, postId);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<BlogPostReaderCardResponse> myBookmarks(UserPrincipal principal, String cursor, Integer limit) {
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
        return CursorPageResponse.<BlogPostReaderCardResponse>builder()
                .items(mapReaderCards(principal, posts))
                .nextCursor(nextCursor)
                .prevCursor(null)
                .hasNext(hasNext)
                .hasPrev(false)
                .limit(resolvedLimit)
                .build();
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<BlogPostReaderCardResponse> premiumLibrary(UserPrincipal principal, UUID serviceId, Integer limit) {
        UUID userId = requireAuthenticated(principal);
        if (serviceId == null || !bookingEligibilityPolicy.hasServiceContentEntitlement(userId, serviceId)) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy thư viện premium");
        }
        int resolvedLimit = resolveLimit(limit);
        List<BlogPost> posts = blogPostRepository.findPremiumLibraryByServiceId(serviceId, PageRequest.of(0, resolvedLimit));
        return CursorPageResponse.<BlogPostReaderCardResponse>builder()
                .items(mapReaderCards(principal, posts.stream().filter(this::isAuthorPubliclyReadable).toList()))
                .nextCursor(null).prevCursor(null).hasNext(false).hasPrev(false).limit(resolvedLimit).build();
    }

    @Transactional
    public BlogFollowResponse followCategory(UserPrincipal principal, UUID categoryId) {
        UUID userId = requireAuthenticated(principal);
        BlogCategory category = loadActiveCategory(categoryId);
        if (!blogCategoryFollowRepository.existsByUserIdAndCategoryId(userId, categoryId)) {
            if (blogCategoryFollowRepository.countByUserId(userId) >= MAX_FOLLOWED_CATEGORIES) {
                throw new BaseException(ErrorCode.BLOG_FOLLOW_LIMIT_REACHED, "Đã đạt giới hạn follow category");
            }
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
    public BlogFollowResponse followMentor(UserPrincipal principal, UUID mentorUserId) {
        UUID userId = requireAuthenticated(principal);
        var summaries = mentorQueryPort.getBlogAuthorSummaries(List.of(mentorUserId));
        if (summaries == null || !summaries.containsKey(mentorUserId)) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy mentor đang hoạt động");
        }
        if (!blogMentorFollowRepository.existsByUserIdAndMentorUserId(userId, mentorUserId)) {
            if (blogMentorFollowRepository.countByUserId(userId) >= MAX_FOLLOWED_MENTORS) {
                throw new BaseException(ErrorCode.BLOG_FOLLOW_LIMIT_REACHED, "Đã đạt giới hạn follow mentor");
            }
            try {
                blogMentorFollowRepository.save(BlogMentorFollow.builder()
                        .user(entityManager.getReference(User.class, userId))
                        .mentorUserId(mentorUserId)
                        .build());
                internalTelemetryService.record("BLOG_MENTOR_FOLLOW", userId, "MENTOR", mentorUserId, Map.of());
            } catch (DataIntegrityViolationException ignored) {
                // Idempotent behavior for concurrent follow requests.
            }
        }
        return myFollows(principal);
    }

    @Transactional
    public BlogFollowResponse unfollowMentor(UserPrincipal principal, UUID mentorUserId) {
        UUID userId = requireAuthenticated(principal);
        blogMentorFollowRepository.deleteByUserIdAndMentorUserId(userId, mentorUserId);
        return myFollows(principal);
    }

    @Transactional(readOnly = true)
    public BlogFollowResponse myFollows(UserPrincipal principal) {
        UUID userId = requireAuthenticated(principal);
        List<BlogCategoryResponse> categories = blogCategoryFollowRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(follow -> blogMapper.toCategory(follow.getCategory()))
                .toList();
        List<BlogMentorFollow> follows = blogMentorFollowRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<UUID> mentorUserIds = follows.stream().map(BlogMentorFollow::getMentorUserId).toList();
        Map<UUID, MentorBlogAuthorSummary> summaries = mentorQueryPort.getBlogAuthorSummaries(mentorUserIds);
        List<com.fptu.exe.skillswap.modules.blog.dto.BlogAuthorResponse> mentors = follows.stream()
                .map(follow -> {
                    MentorBlogAuthorSummary summary = summaries.get(follow.getMentorUserId());
                    return new com.fptu.exe.skillswap.modules.blog.dto.BlogAuthorResponse(
                            follow.getMentorUserId(),
                            summary != null ? summary.fullName() : "Mentor",
                            summary != null ? summary.avatarUrl() : null,
                            com.fptu.exe.skillswap.modules.blog.domain.BlogAuthorType.MENTOR
                    );
                })
                .toList();
        return new BlogFollowResponse(categories, mentors);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<BlogPostReaderCardResponse> personalizedFeed(UserPrincipal principal, String cursor, Integer limit) {
        UUID userId = requireAuthenticated(principal);
        int resolvedLimit = resolveLimit(limit);
        Set<UUID> categoryIds = blogCategoryFollowRepository.findCategoryIdsByUserId(userId);
        Set<UUID> mentorIds = blogMentorFollowRepository.findMentorIdsByUserId(userId);
        String filterHash = filterHash("blog-feed|userId=" + userId
                + "|categories=" + canonicalIds(categoryIds)
                + "|mentors=" + canonicalIds(mentorIds));
        DecodedCursor decodedCursor = decodeCursor(cursor, filterHash);
        List<BlogPost> window;
        if (categoryIds.isEmpty() && mentorIds.isEmpty()) {
            window = blogPostRepository.findPublicWindow(
                    allowedVisibilities(principal),
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
                    mentorIds,
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
                "mentorFollowCount", mentorIds.size(),
                "resultCount", items.size()
        ));
        return CursorPageResponse.<BlogPostReaderCardResponse>builder()
                .items(mapReaderCards(principal, items))
                .nextCursor(nextCursor)
                .prevCursor(null)
                .hasNext(hasNext)
                .hasPrev(false)
                .limit(resolvedLimit)
                .build();
    }

    @Transactional(readOnly = true)
    public List<BlogPostReaderCardResponse> recommendations(UserPrincipal principal, String slug, int limit) {
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
        String sessionId = request == null ? null : request.sessionId().trim();
        String dedupeKey = "BLOG_VIEW:" + postId + ":" + viewerKey(principal, sessionId, serverFingerprint);
        if (trendingCache.registerUniqueView(dedupeKey)) {
            blogPostRepository.incrementViewCount(postId);
            signalTrendingChange(postId);
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
    @Cacheable(cacheNames = "catalog", key = "'blogCategories'")
    public List<BlogCategoryResponse> categories() {
        return blogCategoryRepository.findByActiveTrueOrderByDisplayOrderAscNameAsc()
                .stream()
                .map(blogMapper::toCategory)
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "catalog", key = "'blogTags'")
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
        if (!isAuthorPubliclyReadable(post)
                || (post.getVisibility() == BlogVisibility.BOOKED_MEMBERS && !hasPremiumEntitlement(principal, post))
                || (post.getVisibility() != BlogVisibility.BOOKED_MEMBERS && !allowedVisibilities(principal).contains(post.getVisibility()))) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy bài blog");
        }
    }

    private BlogPost loadReadablePost(UserPrincipal principal, UUID postId) {
        BlogPost post = blogPostRepository.findById(postId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy bài blog"));
        ensureReadable(principal, post);
        return post;
    }

    private BlogPost loadReadablePostForEngagement(UserPrincipal principal, UUID postId) {
        BlogPost post = blogPostRepository.findByIdForEngagementUpdate(postId)
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

    private List<BlogPostReaderCardResponse> mapReaderCards(UserPrincipal principal, List<BlogPost> posts) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        return mapHydratedReaderCards(principal, hydrateReaderPosts(posts)).stream()
                .filter(card -> {
                    BlogPost post = posts.stream().filter(candidate -> candidate.getId().equals(card.id())).findFirst().orElse(null);
                    return post != null && isAuthorPubliclyReadable(post);
                })
                .toList();
    }

    private List<BlogPostReaderCardResponse> mapHydratedReaderCards(UserPrincipal principal, List<BlogPost> hydratedPosts) {
        if (hydratedPosts == null || hydratedPosts.isEmpty()) {
            return List.of();
        }
        Map<UUID, BlogEngagementState> engagement = engagementStates(principal, hydratedPosts.stream().map(BlogPost::getId).toList());
        Map<UUID, MentorBlogAuthorSummary> authorSummaries = mentorQueryPort.getBlogAuthorSummaries(
                hydratedPosts.stream().map(post -> post.getAuthorUser().getId()).collect(Collectors.toSet())
        );
        return hydratedPosts.stream()
                .map(post -> blogMapper.toReaderCard(
                        post,
                        engagement.getOrDefault(post.getId(), BlogEngagementState.empty()),
                        authorSummaries.get(post.getAuthorUser().getId())))
                .toList();
    }

    private List<BlogPost> hydrateReaderPostsByIds(List<UUID> rankedIds) {
        if (rankedIds == null || rankedIds.isEmpty()) {
            return List.of();
        }
        List<UUID> uniqueIds = rankedIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<UUID, BlogPost> byId = blogPostRepository.findReaderPostsWithAuthorByIdIn(uniqueIds).stream()
                .collect(Collectors.toMap(BlogPost::getId, Function.identity()));
        blogPostRepository.loadCategoriesByPostIdIn(uniqueIds);
        blogPostRepository.loadTagsByPostIdIn(uniqueIds);
        return uniqueIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private List<BlogPost> hydrateReaderPosts(List<BlogPost> posts) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        return hydrateReaderPostsByIds(posts.stream().map(BlogPost::getId).toList());
    }

    private BlogEngagementMutationResponse engagementMutationResponse(UserPrincipal principal, UUID postId) {
        BlogPost post = loadPost(postId);
        BlogEngagementState engagement = engagementState(principal, postId);
        return new BlogEngagementMutationResponse(
                postId,
                engagement.likedByCurrentUser(),
                engagement.bookmarkedByCurrentUser(),
                post.getLikeCount() == null ? 0L : post.getLikeCount(),
                post.getBookmarkCount() == null ? 0L : post.getBookmarkCount()
        );
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
        return mentorQueryPort.getBlogAuthorSummaries(Set.of(post.getAuthorUser().getId()))
                .get(post.getAuthorUser().getId());
    }

    private List<BlogVisibility> allowedVisibilities(UserPrincipal principal) {
        return principal == null
                ? List.of(BlogVisibility.PUBLIC)
                : List.of(BlogVisibility.PUBLIC, BlogVisibility.AUTHENTICATED);
    }

    private BlogTrendingSegment resolveTrendingSegment(UserPrincipal principal) {
        if (principal == null) {
            return BlogTrendingSegment.ANONYMOUS;
        }
        return BlogTrendingSegment.AUTHENTICATED_MEMBER;
    }

    private List<BlogVisibility> allowedVisibilitiesForSegment(BlogTrendingSegment segment) {
        return switch (segment) {
            case ANONYMOUS -> List.of(BlogVisibility.PUBLIC);
            case AUTHENTICATED_MEMBER -> List.of(BlogVisibility.PUBLIC, BlogVisibility.AUTHENTICATED);
            case ACTIVE_MENTOR -> List.of(BlogVisibility.PUBLIC, BlogVisibility.AUTHENTICATED);
        };
    }

    private boolean hasPremiumEntitlement(UserPrincipal principal, BlogPost post) {
        if (principal == null || post.getEntitledServices() == null || post.getEntitledServices().isEmpty()) {
            return false;
        }
        return post.getEntitledServices().stream()
                .anyMatch(service -> bookingEligibilityPolicy.hasServiceContentEntitlement(principal.getPublicId(), service.getId()));
    }

    private boolean isAuthorPubliclyReadable(BlogPost post) {
        if (post.getAuthorType() != com.fptu.exe.skillswap.modules.blog.domain.BlogAuthorType.MENTOR) {
            return true;
        }
        User author = post.getAuthorUser();
        if (author == null || author.getStatus() == UserStatus.BANNED || author.getStatus() == UserStatus.DELETED) {
            return false;
        }
        return mentorQueryPort.findMentorProfileByUserId(author.getId())
                .map(profile -> profile.getStatus() != MentorStatus.SUSPENDED)
                .orElse(false);
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

    private void signalTrendingChange(UUID postId) {
        eventPublisher.publishEvent(new BlogTrendingRankingChangedEvent(postId));
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

    private String canonicalIds(Collection<UUID> ids) {
        return ids == null ? "" : ids.stream()
                .filter(Objects::nonNull)
                .map(UUID::toString)
                .sorted()
                .collect(Collectors.joining(","));
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
