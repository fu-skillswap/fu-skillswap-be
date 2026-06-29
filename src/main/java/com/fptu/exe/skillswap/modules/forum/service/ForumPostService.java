package com.fptu.exe.skillswap.modules.forum.service;

import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.domain.TagStatus;
import com.fptu.exe.skillswap.modules.catalog.domain.TagType;
import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
import com.fptu.exe.skillswap.modules.forum.domain.ForumComment;
import com.fptu.exe.skillswap.modules.forum.domain.ForumCommentStatus;
import com.fptu.exe.skillswap.modules.forum.domain.ForumActionType;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPost;
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
import com.fptu.exe.skillswap.modules.forum.repository.ForumPostReactionRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumPostRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForumPostService {

    private final ForumPostRepository forumPostRepository;
    private final ForumCommentRepository forumCommentRepository;
    private final ForumPostReactionRepository forumPostReactionRepository;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;
    private final NotificationService notificationService;
    private final ForumTextPolicy forumTextPolicy;
    private final ForumAbuseGuardService forumAbuseGuardService;

    @Transactional(readOnly = true)
    public PageResponse<ForumPostResponse> getPosts(UUID currentUserId, Integer page, Integer size, String keyword, UUID helpTopicId, Boolean mine) {
        User currentUser = requireForumUser(currentUserId);
        Pageable pageable = PageRequest.of(defaultPage(page), defaultSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
        String keywordPattern = toKeywordPattern(keyword);
        UUID authorId = Boolean.TRUE.equals(mine) ? currentUser.getId() : null;
        Page<ForumPost> postPage = forumPostRepository.searchPublicPosts(
                ForumPostStatus.PUBLISHED,
                helpTopicId,
                authorId,
                keywordPattern,
                pageable
        );
        return toPostPageResponse(postPage.map(post -> toPostResponse(post, currentUser.getId())));
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
        ForumPost post = ForumPost.builder()
                .authorUser(currentUser)
                .helpTopic(helpTopic)
                .title(forumTextPolicy.requirePlainText(request.title(), "Tiêu đề bài viết"))
                .content(forumTextPolicy.requirePlainText(request.content(), "Nội dung bài viết"))
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
        post.setHelpTopic(helpTopic);
        return toPostResponse(forumPostRepository.save(post), currentUser.getId());
    }

    @Transactional
    public ForumPostResponse deletePost(UUID currentUserId, UUID postId) {
        User currentUser = requireForumUser(currentUserId);
        ForumPost post = loadOwnedEditablePost(postId, currentUser.getId());
        ForumPostResponse response = toPostResponse(post, currentUser.getId());
        forumPostRepository.delete(post);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<ForumCommentResponse> getComments(UUID currentUserId, UUID postId, Integer page, Integer size) {
        requireForumUser(currentUserId);
        ForumPost post = forumPostRepository.findByIdAndStatus(postId, ForumPostStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài viết forum"));
        Pageable pageable = PageRequest.of(defaultPage(page), defaultSize(size), Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<ForumComment> commentPage = forumCommentRepository.findByPostIdAndStatus(post.getId(), ForumCommentStatus.VISIBLE, pageable);
        return toCommentPageResponse(commentPage.map(this::toCommentResponse));
    }

    @Transactional
    public ForumCommentResponse createComment(UUID currentUserId, UUID postId, ForumCommentUpsertRequest request) {
        User currentUser = requireForumUser(currentUserId);
        forumAbuseGuardService.checkAndLog(currentUser, ForumActionType.CREATE_COMMENT);
        ForumPost post = forumPostRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài viết forum"));
        ensurePostVisible(post);

        ForumComment comment = ForumComment.builder()
                .post(post)
                .authorUser(currentUser)
                .content(forumTextPolicy.requirePlainText(request.content(), "Nội dung bình luận"))
                .status(ForumCommentStatus.VISIBLE)
                .reportCount(0)
                .build();
        ForumComment saved = forumCommentRepository.save(comment);
        post.setCommentCount(safeIncrement(post.getCommentCount()));
        post.setLastActivityAt(DateTimeUtil.now());
        forumPostRepository.save(post);

        if (!currentUser.getId().equals(post.getAuthorUser().getId())) {
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

        return toCommentResponse(saved);
    }

    @Transactional
    public ForumCommentResponse updateComment(UUID currentUserId, UUID commentId, ForumCommentUpsertRequest request) {
        User currentUser = requireForumUser(currentUserId);
        ForumComment comment = loadOwnedEditableComment(commentId, currentUser.getId());
        comment.setContent(forumTextPolicy.requirePlainText(request.content(), "Nội dung bình luận"));
        return toCommentResponse(forumCommentRepository.save(comment));
    }

    @Transactional
    public ForumCommentResponse deleteComment(UUID currentUserId, UUID commentId) {
        User currentUser = requireForumUser(currentUserId);
        ForumComment comment = loadOwnedEditableComment(commentId, currentUser.getId());
        ForumPost post = forumPostRepository.findByIdForUpdate(comment.getPost().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài viết forum"));
        ForumCommentResponse response = toCommentResponse(comment);
        forumCommentRepository.delete(comment);
        if (post.getCommentCount() != null && post.getCommentCount() > 0) {
            post.setCommentCount(post.getCommentCount() - 1);
            forumPostRepository.save(post);
        }
        return response;
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
                .reactedByCurrentUser(reaction.isPresent())
                .myReactionType(reaction.map(value -> value.getReactionType().name()).orElse(null))
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
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

    private ForumCommentResponse toCommentResponse(ForumComment comment) {
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
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }

    private ForumHelpTopicResponse toHelpTopicResponse(Tag tag) {
        return ForumHelpTopicResponse.builder()
                .id(tag.getId())
                .code(tag.getCode())
                .nameVi(tag.getNameVi())
                .nameEn(tag.getNameEn())
                .build();
    }

    private PageResponse<ForumPostResponse> toPostPageResponse(Page<ForumPostResponse> page) {
        return PageResponse.<ForumPostResponse>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    private PageResponse<ForumCommentResponse> toCommentPageResponse(Page<ForumCommentResponse> page) {
        return PageResponse.<ForumCommentResponse>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    private int defaultPage(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    private int defaultSize(Integer size) {
        int resolved = size == null || size <= 0 ? 20 : size;
        return Math.min(resolved, 50);
    }

    private String toKeywordPattern(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        return "%" + keyword.trim().toLowerCase() + "%";
    }

    private int safeIncrement(Integer value) {
        return defaultInt(value) + 1;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }
}
