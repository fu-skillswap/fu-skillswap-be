package com.fptu.exe.skillswap.modules.blog.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.blog.domain.BlogAudienceType;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPost;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPostStatus;
import com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility;
import com.fptu.exe.skillswap.modules.blog.dto.BlogCategoryResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogPostCardResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogPostDetailResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogTagResponse;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogAuthorCtaClickRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogViewRequest;
import com.fptu.exe.skillswap.modules.blog.repository.BlogCategoryRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogPostRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogTagRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class BlogService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final String DIRECTION_NEXT = "NEXT";

    private final BlogPostRepository blogPostRepository;
    private final BlogCategoryRepository blogCategoryRepository;
    private final BlogTagRepository blogTagRepository;
    private final BlogMapper blogMapper;
    private final CursorCodec cursorCodec;
    private final BlogContentPolicy contentPolicy;
    private final MentorContentAccessService mentorContentAccessService;
    private final InternalTelemetryService internalTelemetryService;

    private final Cache<String, Boolean> viewDedupeCache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(100_000)
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
                .items(items.stream().map(blogMapper::toCard).toList())
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
                .map(blogMapper::toCard)
                .toList();
    }

    @Transactional(readOnly = true)
    public BlogPostDetailResponse getBySlug(UserPrincipal principal, String slug) {
        BlogPost post = blogPostRepository.findBySlug(slug)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy bài blog"));
        ensureReadable(principal, post);
        return blogMapper.toDetail(post);
    }

    @Transactional
    public void recordView(UserPrincipal principal, UUID postId, BlogViewRequest request) {
        BlogPost post = blogPostRepository.findById(postId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy bài blog"));
        ensureReadable(principal, post);
        String dedupeKey = "BLOG_VIEW:" + postId + ":" + viewerKey(principal, request == null ? null : request.sessionId());
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

    private String viewerKey(UserPrincipal principal, String sessionId) {
        if (principal != null) {
            return "u:" + principal.getPublicId();
        }
        if (contentPolicy.hasText(sessionId)) {
            return "s:" + sessionId.trim();
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
