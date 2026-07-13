package com.fptu.exe.skillswap.modules.forum.service;

import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.domain.TagStatus;
import com.fptu.exe.skillswap.modules.catalog.domain.TagType;
import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
import com.fptu.exe.skillswap.modules.forum.domain.ForumComment;
import com.fptu.exe.skillswap.modules.forum.domain.ForumCommentStatus;
import com.fptu.exe.skillswap.modules.forum.domain.ForumActionType;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPost;
import com.fptu.exe.skillswap.modules.forum.domain.ForumCommentReaction;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPostReaction;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPostStatus;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReactionType;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumCommentUpsertRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumPostUpsertRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumReactionRequest;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumCommentResponse;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumHelpTopicResponse;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumPostResponse;
import com.fptu.exe.skillswap.modules.forum.repository.ForumCommentRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumCommentReactionRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumPostReactionRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumPostRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumPostSpecification;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.shared.cursor.CursorCodec;
import com.fptu.exe.skillswap.shared.cursor.CursorTokenPayload;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForumPostService {

    private final ForumPostRepository forumPostRepository;
    private final ForumCommentRepository forumCommentRepository;
    private final ForumPostReactionRepository forumPostReactionRepository;
    private final ForumCommentReactionRepository forumCommentReactionRepository;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;
    private final NotificationService notificationService;
    private final ForumTextPolicy forumTextPolicy;
    private final ForumAbuseGuardService forumAbuseGuardService;
    private final CursorCodec cursorCodec;

    @Transactional(readOnly = true)
    public CursorPageResponse<ForumPostResponse> getPosts(UUID currentUserId, String cursor, Integer limit, String keyword, UUID helpTopicId, Boolean mine) {
        User currentUser = requireForumUser(currentUserId);
        int resolvedLimit = defaultLimit(limit);
        String keywordPattern = toKeywordPattern(keyword);
        String filterHash = buildUserPostFilterHash(keyword, helpTopicId, mine);
        DecodedPostCursor decodedCursor = decodePostCursor(cursor, filterHash);
        Specification<ForumPost> specification = buildUserPostSpecification(currentUser.getId(), keywordPattern, helpTopicId, mine, decodedCursor);
        List<ForumPost> postWindow = forumPostRepository.findWindow(specification, resolvedLimit + 1);
        boolean hasNext = postWindow.size() > resolvedLimit;
        List<ForumPost> visiblePosts = hasNext ? new ArrayList<>(postWindow.subList(0, resolvedLimit)) : postWindow;
        Set<UUID> reactedPostIds = loadReactedPostIds(
                currentUser.getId(),
                visiblePosts.stream().map(ForumPost::getId).toList()
        );
        List<ForumPostResponse> items = visiblePosts.stream()
                .map(post -> toPostResponse(
                        post,
                        reactedPostIds.contains(post.getId()),
                        reactedPostIds.contains(post.getId()) ? ForumReactionType.LIKE.name() : null
                ))
                .toList();
        String nextCursor = hasNext && !visiblePosts.isEmpty()
                ? encodeNextCursor(visiblePosts.get(visiblePosts.size() - 1), filterHash)
                : null;
        return CursorPageResponse.<ForumPostResponse>builder()
                .items(items)
                .nextCursor(nextCursor)
                .prevCursor(null)
                .hasNext(hasNext)
                .hasPrev(false)
                .limit(resolvedLimit)
                .build();
    }

    @Transactional(readOnly = true)
    public ForumPostResponse getPostDetail(UUID currentUserId, UUID postId) {
        User currentUser = requireForumUser(currentUserId);
        ForumPost post = forumPostRepository.findByIdAndStatus(postId, ForumPostStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài viết forum"));
        return toPostResponse(post, currentUser.getId());
    }

    @Transactional
    public ForumPostResponse createPost(UUID currentUserId, ForumPostUpsertRequest request) {
        User currentUser = requireForumUser(currentUserId);
        forumAbuseGuardService.checkAndLog(currentUser, ForumActionType.CREATE_POST);
        Tag helpTopic = requireHelpTopic(request.helpTopicId());
        String normalizedTitle = forumTextPolicy.requirePlainText(request.title(), "Tiêu đề bài viết");
        String normalizedContent = forumTextPolicy.requirePlainText(request.content(), "Nội dung bài viết");
        if (forumPostRepository.existsRecentDuplicatePost(
                currentUser.getId(),
                normalizedTitle,
                normalizedContent,
                DateTimeUtil.now().minusMinutes(15)
        )) {
            throw new BaseException(ErrorCode.TOO_MANY_REQUESTS, "Bạn vừa đăng nội dung này rồi, vui lòng tránh đăng trùng lặp");
        }
        java.util.List<String> cleanedImages = cleanImageUrls(request.imageUrls());
        ForumPost post = ForumPost.builder()
                .authorUser(currentUser)
                .helpTopic(helpTopic)
                .title(normalizedTitle)
                .content(normalizedContent)
                .imageUrls(cleanedImages)
                .status(ForumPostStatus.PUBLISHED)
                .commentCount(0)
                .reactionCount(0)
                .reportCount(0)
                .lastActivityAt(DateTimeUtil.now())
                .build();
        return toPostResponse(forumPostRepository.save(post), currentUser.getId());
    }

    @Transactional
    public ForumPostResponse updatePost(UUID currentUserId, UUID postId, ForumPostUpsertRequest request) {
        User currentUser = requireForumUser(currentUserId);
        ForumPost post = loadOwnedEditablePost(postId, currentUser.getId());
        Tag helpTopic = requireHelpTopic(request.helpTopicId());
        post.setTitle(forumTextPolicy.requirePlainText(request.title(), "Tiêu đề bài viết"));
        post.setContent(forumTextPolicy.requirePlainText(request.content(), "Nội dung bài viết"));
        post.setImageUrls(cleanImageUrls(request.imageUrls()));
        post.setHelpTopic(helpTopic);
        return toPostResponse(forumPostRepository.save(post), currentUser.getId());
    }

    @Transactional
    public ForumPostResponse deletePost(UUID currentUserId, UUID postId) {
        User currentUser = requireForumUser(currentUserId);
        ForumPost post = loadOwnedEditablePost(postId, currentUser.getId());
        ForumPostResponse response = toPostResponse(post, currentUser.getId());
        forumCommentReactionRepository.deleteByPostId(postId);
        forumPostReactionRepository.deleteByPostId(postId);
        forumCommentRepository.softDeleteByPostId(postId);
        forumPostRepository.delete(post);
        return response;
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<ForumCommentResponse> getComments(UUID currentUserId, UUID postId, String cursor, Integer limit) {
        requireForumUser(currentUserId);
        ForumPost post = forumPostRepository.findByIdAndStatus(postId, ForumPostStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài viết forum"));
        int resolvedLimit = defaultLimit(limit);
        String filterHash = buildUserCommentFilterHash(postId);
        DecodedCommentCursor decodedCursor = decodeCommentCursor(cursor, filterHash, "comment");
        List<ForumComment> commentWindow = forumCommentRepository.findVisibleCommentsWindow(
                post.getId(),
                ForumCommentStatus.VISIBLE,
                decodedCursor.createdAt(),
                decodedCursor.commentId(),
                resolvedLimit + 1
        );
        boolean hasNext = commentWindow.size() > resolvedLimit;
        List<ForumComment> visibleComments = hasNext ? new ArrayList<>(commentWindow.subList(0, resolvedLimit)) : commentWindow;
        Set<UUID> reactedCommentIds = loadReactedCommentIds(
                currentUserId,
                visibleComments.stream().map(ForumComment::getId).toList()
        );
        Map<UUID, ForumComment> replyParentsById = loadReplyParentsById(visibleComments);
        List<ForumCommentResponse> items = visibleComments.stream()
                .map(comment -> toCommentResponse(comment, reactedCommentIds.contains(comment.getId()), replyParentsById))
                .toList();
        String nextCursor = hasNext && !visibleComments.isEmpty()
                ? encodeNextCommentCursor(visibleComments.get(visibleComments.size() - 1), filterHash)
                : null;
        return CursorPageResponse.<ForumCommentResponse>builder()
                .items(items)
                .nextCursor(nextCursor)
                .prevCursor(null)
                .hasNext(hasNext)
                .hasPrev(false)
                .limit(resolvedLimit)
                .build();
    }

    @Transactional
    public ForumCommentResponse createComment(UUID currentUserId, UUID postId, ForumCommentUpsertRequest request) {
        User currentUser = requireForumUser(currentUserId);
        forumAbuseGuardService.checkAndLog(currentUser, ForumActionType.CREATE_COMMENT);
        ForumPost post = forumPostRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài viết forum"));
        ensurePostVisible(post);
        String normalizedContent = forumTextPolicy.requirePlainText(request.content(), "Nội dung bình luận");
        if (forumCommentRepository.existsRecentDuplicateComment(
                post.getId(),
                currentUser.getId(),
                normalizedContent,
                DateTimeUtil.now().minusMinutes(5)
        )) {
            throw new BaseException(ErrorCode.TOO_MANY_REQUESTS, "Bạn vừa gửi bình luận này rồi, vui lòng tránh spam lặp nội dung");
        }

        ForumComment parentComment = null;
        if (request.replyToCommentId() != null) {
            parentComment = forumCommentRepository.findById(request.replyToCommentId())
                    .orElseThrow(() -> new BaseException(ErrorCode.BAD_REQUEST, "Bình luận gốc không tồn tại"));
            if (parentComment.getStatus() != ForumCommentStatus.VISIBLE) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Không thể reply bình luận đã bị ẩn hoặc xóa");
            }
            if (!parentComment.getPost().getId().equals(post.getId())) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Bình luận gốc không thuộc bài viết này");
            }
            if (parentComment.getReplyToCommentId() != null) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Chỉ hỗ trợ trả lời bình luận 1 cấp (Không thể reply một reply)");
            }
        }

        java.util.List<String> cleanedImages = cleanImageUrls(request.imageUrls());
        ForumComment comment = ForumComment.builder()
                .post(post)
                .authorUser(currentUser)
                .content(normalizedContent)
                .imageUrls(cleanedImages)
                .status(ForumCommentStatus.VISIBLE)
                .reportCount(0)
                .reactionCount(0)
                .replyToCommentId(parentComment != null ? parentComment.getId() : null)
                .build();
        ForumComment saved = forumCommentRepository.save(comment);
        post.setCommentCount(safeIncrement(post.getCommentCount()));
        post.setLastActivityAt(DateTimeUtil.now());
        forumPostRepository.save(post);

        boolean isSelfReply = parentComment != null && currentUser.getId().equals(parentComment.getAuthorUser().getId());
        boolean isSelfComment = currentUser.getId().equals(post.getAuthorUser().getId());

        if (parentComment != null) {
            if (!isSelfReply) {
                try {
                    notificationService.createNotification(
                            parentComment.getAuthorUser().getId(),
                            NotificationType.FORUM_COMMENT_REPLY,
                            "Bình luận của bạn có người trả lời",
                            currentUser.getFullName() + " vừa trả lời bình luận của bạn trong bài viết forum.",
                            "FORUM_POST",
                            post.getId()
                    );
                } catch (RuntimeException ex) {
                    log.warn("Không thể tạo notification cho forum reply commentId={}: {}", saved.getId(), ex.getMessage());
                }
            }
        } else {
            if (!isSelfComment) {
                try {
                    notificationService.createNotification(
                            post.getAuthorUser().getId(),
                            NotificationType.FORUM_POST_COMMENTED,
                            "Bài viết của bạn có bình luận mới",
                            currentUser.getFullName() + " vừa bình luận vào bài viết forum của bạn.",
                            "FORUM_POST",
                            post.getId()
                    );
                } catch (RuntimeException ex) {
                    log.warn("Không thể tạo notification cho comment forum postId={}: {}", post.getId(), ex.getMessage());
                }
            }
        }

        Map<UUID, ForumComment> replyParentsById = parentComment == null
                ? Map.of()
                : Map.of(parentComment.getId(), parentComment);
        return toCommentResponse(saved, false, replyParentsById);
    }

    @Transactional
    public ForumCommentResponse updateComment(UUID currentUserId, UUID commentId, ForumCommentUpsertRequest request) {
        User currentUser = requireForumUser(currentUserId);
        ForumComment comment = loadOwnedEditableComment(commentId, currentUser.getId());
        comment.setContent(forumTextPolicy.requirePlainText(request.content(), "Nội dung bình luận"));
        comment.setImageUrls(cleanImageUrls(request.imageUrls()));
        return toCommentResponse(forumCommentRepository.save(comment), currentUserId);
    }

    @Transactional
    public ForumCommentResponse deleteComment(UUID currentUserId, UUID commentId) {
        User currentUser = requireForumUser(currentUserId);
        ForumComment comment = loadOwnedEditableComment(commentId, currentUser.getId());
        ForumPost post = forumPostRepository.findByIdForUpdate(comment.getPost().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài viết forum"));
        ForumCommentResponse response = toCommentResponse(comment, currentUserId);
        List<ForumComment> visibleReplies = forumCommentRepository.findByReplyToCommentIdAndStatus(comment.getId(), ForumCommentStatus.VISIBLE);
        visibleReplies.forEach(forumCommentRepository::delete);
        forumCommentRepository.delete(comment);
        int removedCount = 1 + visibleReplies.size();
        if (post.getCommentCount() != null && post.getCommentCount() > 0) {
            post.setCommentCount(Math.max(0, post.getCommentCount() - removedCount));
            forumPostRepository.save(post);
        }
        return response;
    }

    @Transactional
    public ForumCommentResponse upsertCommentReaction(UUID currentUserId, UUID commentId, ForumReactionRequest request) {
        User currentUser = requireForumUser(currentUserId);
        forumAbuseGuardService.checkAndLog(currentUser, ForumActionType.TOGGLE_REACTION);
        if (request.reactionType() != ForumReactionType.LIKE) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Forum MVP hiện chỉ hỗ trợ reaction LIKE cho comment");
        }

        ForumComment comment = forumCommentRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bình luận forum"));
        if (comment.getStatus() != ForumCommentStatus.VISIBLE) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Không thể thả reaction cho bình luận đã bị ẩn hoặc xóa");
        }
        ensurePostVisible(comment.getPost());

        Optional<ForumCommentReaction> existing =
                forumCommentReactionRepository.findByCommentIdAndUserId(commentId, currentUser.getId());
        if (existing.isEmpty()) {
            ForumCommentReaction reaction = ForumCommentReaction.builder()
                    .comment(comment)
                    .user(currentUser)
                    .reactionType(ForumReactionType.LIKE)
                    .build();
            forumCommentReactionRepository.save(reaction);
            comment.setReactionCount(safeIncrement(comment.getReactionCount()));
            forumCommentRepository.save(comment);
        }
        return toCommentResponse(comment, currentUserId);
    }

    @Transactional
    public ForumCommentResponse removeCommentReaction(UUID currentUserId, UUID commentId) {
        User currentUser = requireForumUser(currentUserId);
        forumAbuseGuardService.checkAndLog(currentUser, ForumActionType.TOGGLE_REACTION);
        ForumComment comment = forumCommentRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bình luận forum"));
        if (comment.getStatus() != ForumCommentStatus.VISIBLE) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Không thể thao tác trên bình luận đã bị ẩn hoặc xóa");
        }
        ensurePostVisible(comment.getPost());
        
        forumCommentReactionRepository.findByCommentIdAndUserId(commentId, currentUser.getId()).ifPresent(reaction -> {
            forumCommentReactionRepository.delete(reaction);
            if (comment.getReactionCount() != null && comment.getReactionCount() > 0) {
                comment.setReactionCount(comment.getReactionCount() - 1);
                forumCommentRepository.save(comment);
            }
        });
        return toCommentResponse(comment, currentUserId);
    }

    @Transactional
    public ForumPostResponse upsertReaction(UUID currentUserId, UUID postId, ForumReactionRequest request) {
        User currentUser = requireForumUser(currentUserId);
        forumAbuseGuardService.checkAndLog(currentUser, ForumActionType.TOGGLE_REACTION);
        if (request.reactionType() != ForumReactionType.LIKE) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Forum MVP hiện chỉ hỗ trợ reaction LIKE");
        }

        ForumPost post = forumPostRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài viết forum"));
        ensurePostVisible(post);

        Optional<ForumPostReaction> existing = forumPostReactionRepository.findByPostIdAndUserId(postId, currentUser.getId());
        if (existing.isEmpty()) {
            ForumPostReaction reaction = ForumPostReaction.builder()
                    .post(post)
                    .user(currentUser)
                    .reactionType(ForumReactionType.LIKE)
                    .build();
            forumPostReactionRepository.save(reaction);
            post.setReactionCount(safeIncrement(post.getReactionCount()));
            forumPostRepository.save(post);
        }
        return toPostResponse(post, currentUser.getId());
    }

    @Transactional
    public ForumPostResponse removeReaction(UUID currentUserId, UUID postId) {
        User currentUser = requireForumUser(currentUserId);
        forumAbuseGuardService.checkAndLog(currentUser, ForumActionType.TOGGLE_REACTION);
        ForumPost post = forumPostRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài viết forum"));
        ensurePostVisible(post);
        forumPostReactionRepository.findByPostIdAndUserId(postId, currentUser.getId()).ifPresent(reaction -> {
            forumPostReactionRepository.delete(reaction);
            if (post.getReactionCount() != null && post.getReactionCount() > 0) {
                post.setReactionCount(post.getReactionCount() - 1);
                forumPostRepository.save(post);
            }
        });
        return toPostResponse(post, currentUser.getId());
    }

    User requireForumUser(UUID currentUserId) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Chỉ tài khoản đang hoạt động mới được sử dụng forum");
        }
        boolean eligibleRole = user.getRoles().contains(RoleCode.MENTEE) || user.getRoles().contains(RoleCode.MENTOR);
        boolean adminRole = user.getRoles().contains(RoleCode.ADMIN) || user.getRoles().contains(RoleCode.SYSTEM_ADMIN);
        if (!eligibleRole || adminRole) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền sử dụng forum người dùng");
        }
        return user;
    }

    ForumPost requireVisiblePost(UUID postId) {
        ForumPost post = forumPostRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài viết forum"));
        ensurePostVisible(post);
        return post;
    }

    ForumComment requireVisibleComment(UUID commentId) {
        ForumComment comment = forumCommentRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bình luận forum"));
        if (comment.getStatus() != ForumCommentStatus.VISIBLE) {
            throw new ResourceNotFoundException("Không tìm thấy bình luận forum");
        }
        ensurePostVisible(comment.getPost());
        return comment;
    }

    private ForumPost loadOwnedEditablePost(UUID postId, UUID currentUserId) {
        ForumPost post = forumPostRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài viết forum"));
        if (!post.getAuthorUser().getId().equals(currentUserId)) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền chỉnh sửa bài viết này");
        }
        if (post.getStatus() != ForumPostStatus.PUBLISHED) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Bài viết đang bị ẩn nên không thể chỉnh sửa");
        }
        return post;
    }

    private ForumComment loadOwnedEditableComment(UUID commentId, UUID currentUserId) {
        ForumComment comment = forumCommentRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bình luận forum"));
        if (!comment.getAuthorUser().getId().equals(currentUserId)) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền chỉnh sửa bình luận này");
        }
        if (comment.getStatus() != ForumCommentStatus.VISIBLE) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Bình luận đang bị ẩn nên không thể chỉnh sửa");
        }
        ensurePostVisible(comment.getPost());
        return comment;
    }

    private Tag requireHelpTopic(UUID helpTopicId) {
        if (helpTopicId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "helpTopicId là bắt buộc");
        }
        Tag tag = tagRepository.findById(helpTopicId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy help topic"));
        if (tag.getType() != TagType.HELP_TOPIC || tag.getStatus() != TagStatus.ACTIVE) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Help topic không hợp lệ hoặc chưa hoạt động");
        }
        return tag;
    }

    private void ensurePostVisible(ForumPost post) {
        if (post.getStatus() != ForumPostStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Không tìm thấy bài viết forum");
        }
    }

    private ForumPostResponse toPostResponse(ForumPost post, UUID currentUserId) {
        Optional<ForumPostReaction> reaction = currentUserId == null
                ? Optional.empty()
                : forumPostReactionRepository.findByPostIdAndUserId(post.getId(), currentUserId);
        return toPostResponse(
                post,
                reaction.isPresent(),
                reaction.map(value -> value.getReactionType().name()).orElse(null)
        );
    }

    private ForumPostResponse toPostResponse(ForumPost post, boolean reactedByCurrentUser, String myReactionType) {
        return ForumPostResponse.builder()
                .postId(post.getId())
                .authorUserId(post.getAuthorUser().getId())
                .authorFullName(post.getAuthorUser().getFullName())
                .authorAvatarUrl(post.getAuthorUser().getAvatarUrl())
                .helpTopic(toHelpTopicResponse(post.getHelpTopic()))
                .title(post.getTitle())
                .content(post.getContent())
                .status(post.getStatus().name())
                .commentCount(defaultInt(post.getCommentCount()))
                .reactionCount(defaultInt(post.getReactionCount()))
                .reportCount(defaultInt(post.getReportCount()))
                .lastActivityAt(post.getLastActivityAt())
                .reactedByCurrentUser(reactedByCurrentUser)
                .myReactionType(myReactionType)
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .imageUrls(post.getImageUrls())
                .build();
    }

    private Set<UUID> loadReactedPostIds(UUID currentUserId, Collection<UUID> postIds) {
        if (currentUserId == null || postIds == null || postIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(forumPostReactionRepository.findReactedPostIdsByUserIdAndPostIdIn(currentUserId, postIds));
    }

    private Set<UUID> loadReactedCommentIds(UUID currentUserId, Collection<UUID> commentIds) {
        if (currentUserId == null || commentIds == null || commentIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(forumCommentReactionRepository.findReactedCommentIdsByUserIdAndCommentIdIn(currentUserId, commentIds));
    }

    private String determineAuthorRole(java.util.Set<RoleCode> roles) {
        if (roles == null) {
            return "MENTEE";
        }
        if (roles.contains(RoleCode.MENTOR)) {
            return "MENTOR";
        }
        if (roles.contains(RoleCode.MENTEE)) {
            return "MENTEE";
        }
        return "MENTEE";
    }

    private ForumCommentResponse toCommentResponse(ForumComment comment, UUID currentUserId) {
        Optional<ForumCommentReaction> reaction = currentUserId == null
                ? Optional.empty()
                : forumCommentReactionRepository.findByCommentIdAndUserId(comment.getId(), currentUserId);
        return toCommentResponse(comment, reaction.isPresent(), loadReplyParentsById(List.of(comment)));
    }

    private ForumCommentResponse toCommentResponse(ForumComment comment,
                                                   boolean reactedByCurrentUser,
                                                   Map<UUID, ForumComment> replyParentsById) {
        ForumComment replyParent = comment.getReplyToCommentId() == null
                ? null
                : replyParentsById.get(comment.getReplyToCommentId());

        return ForumCommentResponse.builder()
                .commentId(comment.getId())
                .postId(comment.getPost().getId())
                .authorUserId(comment.getAuthorUser().getId())
                .authorFullName(comment.getAuthorUser().getFullName())
                .authorAvatarUrl(comment.getAuthorUser().getAvatarUrl())
                .authorRole(determineAuthorRole(comment.getAuthorUser().getRoles()))
                .content(comment.getContent())
                .status(comment.getStatus().name())
                .reportCount(defaultInt(comment.getReportCount()))
                .reactionCount(defaultInt(comment.getReactionCount()))
                .reactedByCurrentUser(reactedByCurrentUser)
                .replyToCommentId(comment.getReplyToCommentId())
                .replyToUserId(replyParent == null ? null : replyParent.getAuthorUser().getId())
                .replyToUserName(replyParent == null ? null : replyParent.getAuthorUser().getFullName())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .imageUrls(comment.getImageUrls())
                .build();
    }

    private Map<UUID, ForumComment> loadReplyParentsById(Collection<ForumComment> comments) {
        if (comments == null || comments.isEmpty()) {
            return Map.of();
        }
        List<UUID> parentIds = comments.stream()
                .map(ForumComment::getReplyToCommentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (parentIds.isEmpty()) {
            return Map.of();
        }
        return forumCommentRepository.findByIdIn(parentIds).stream()
                .collect(Collectors.toMap(ForumComment::getId, Function.identity()));
    }

    private ForumHelpTopicResponse toHelpTopicResponse(Tag tag) {
        return ForumHelpTopicResponse.builder()
                .id(tag.getId())
                .code(tag.getCode())
                .nameVi(tag.getNameVi())
                .nameEn(tag.getNameEn())
                .build();
    }

    private int defaultLimit(Integer limit) {
        int resolved = limit == null || limit <= 0 ? 20 : limit;
        return Math.min(resolved, 50);
    }

    private String toKeywordPattern(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        return "%" + keyword.trim().toLowerCase() + "%";
    }

    private String buildUserPostFilterHash(String keyword, UUID helpTopicId, Boolean mine) {
        return "forum-posts:user|keyword=" + normalizeKeywordFilterValue(keyword)
                + "|helpTopicId=" + normalizeFilterValue(helpTopicId)
                + "|mine=" + Boolean.TRUE.equals(mine)
                + "|status=" + ForumPostStatus.PUBLISHED.name();
    }

    private String buildUserCommentFilterHash(UUID postId) {
        return "forum-comments:user|postId=" + normalizeFilterValue(postId)
                + "|status=" + ForumCommentStatus.VISIBLE.name();
    }

    private Specification<ForumPost> buildUserPostSpecification(UUID currentUserId,
                                                                String keywordPattern,
                                                                UUID helpTopicId,
                                                                Boolean mine,
                                                                DecodedPostCursor decodedCursor) {
        Specification<ForumPost> specification = Specification.where(ForumPostSpecification.hasStatus(ForumPostStatus.PUBLISHED))
                .and(ForumPostSpecification.hasHelpTopic(helpTopicId))
                .and(ForumPostSpecification.hasKeyword(keywordPattern));
        if (Boolean.TRUE.equals(mine)) {
            specification = specification.and(ForumPostSpecification.mineOnly(currentUserId));
        }
        return specification.and(ForumPostSpecification.isBeforeCursor(decodedCursor.lastActivityAt(), decodedCursor.postId()));
    }

    private DecodedPostCursor decodePostCursor(String cursor, String expectedFilterHash) {
        if (cursor == null || cursor.isBlank()) {
            return DecodedPostCursor.empty();
        }
        CursorTokenPayload payload = cursorCodec.decode(cursor);
        if (!Objects.equals(expectedFilterHash, payload.filterHash())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cursor không khớp với bộ lọc hiện tại");
        }
        if (payload.sortKey() == null || payload.secondaryKey() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cursor không hợp lệ");
        }
        return new DecodedPostCursor(
                parseCursorDateTime(payload.sortKey()),
                parseCursorPostId(payload.secondaryKey())
        );
    }

    private String encodeNextCursor(ForumPost post, String filterHash) {
        return cursorCodec.encode(CursorTokenPayload.builder()
                .sortKey(post.getLastActivityAt().toString())
                .secondaryKey(post.getId().toString())
                .direction("NEXT")
                .filterHash(filterHash)
                .issuedAt(Instant.now())
                .build());
    }

    private DecodedCommentCursor decodeCommentCursor(String cursor, String expectedFilterHash, String entityLabel) {
        if (cursor == null || cursor.isBlank()) {
            return DecodedCommentCursor.empty();
        }
        CursorTokenPayload payload = cursorCodec.decode(cursor);
        if (!Objects.equals(expectedFilterHash, payload.filterHash())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cursor không khớp với bộ lọc hiện tại");
        }
        if (payload.sortKey() == null || payload.secondaryKey() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cursor không hợp lệ");
        }
        return new DecodedCommentCursor(
                parseCursorDateTime(payload.sortKey()),
                parseCursorUuid(payload.secondaryKey(), entityLabel)
        );
    }

    private String encodeNextCommentCursor(ForumComment comment, String filterHash) {
        return cursorCodec.encode(CursorTokenPayload.builder()
                .sortKey(comment.getCreatedAt().toString())
                .secondaryKey(comment.getId().toString())
                .direction("NEXT")
                .filterHash(filterHash)
                .issuedAt(Instant.now())
                .build());
    }

    private LocalDateTime parseCursorDateTime(String rawValue) {
        try {
            return LocalDateTime.parse(rawValue);
        } catch (DateTimeParseException ex) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cursor chứa lastActivityAt không hợp lệ", ex);
        }
    }

    private UUID parseCursorPostId(String rawValue) {
        return parseCursorUuid(rawValue, "post");
    }

    private UUID parseCursorUuid(String rawValue, String entityLabel) {
        try {
            return UUID.fromString(rawValue);
        } catch (IllegalArgumentException ex) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cursor chứa " + entityLabel + "Id không hợp lệ", ex);
        }
    }

    private String normalizeFilterValue(Object value) {
        if (value == null) {
            return "_";
        }
        String normalized = value.toString().trim();
        return normalized.isEmpty() ? "_" : normalized;
    }

    private String normalizeKeywordFilterValue(String value) {
        if (value == null) {
            return "_";
        }
        String normalized = value.trim().toLowerCase();
        return normalized.isEmpty() ? "_" : normalized;
    }

    private java.util.List<String> cleanImageUrls(java.util.List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        return raw.stream()
                .filter(url -> url != null && !url.isBlank())
                .map(String::trim)
                .toList();
    }

    private int safeIncrement(Integer value) {
        return defaultInt(value) + 1;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private record DecodedPostCursor(LocalDateTime lastActivityAt, UUID postId) {
        private static DecodedPostCursor empty() {
            return new DecodedPostCursor(null, null);
        }
    }

    private record DecodedCommentCursor(LocalDateTime createdAt, UUID commentId) {
        private static DecodedCommentCursor empty() {
            return new DecodedCommentCursor(null, null);
        }
    }
}
