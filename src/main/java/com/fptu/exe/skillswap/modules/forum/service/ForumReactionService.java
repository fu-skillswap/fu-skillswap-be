package com.fptu.exe.skillswap.modules.forum.service;

import com.fptu.exe.skillswap.modules.forum.domain.ForumActionType;
import com.fptu.exe.skillswap.modules.forum.domain.ForumComment;
import com.fptu.exe.skillswap.modules.forum.domain.ForumCommentReaction;
import com.fptu.exe.skillswap.modules.forum.domain.ForumCommentStatus;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPost;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPostReaction;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPostStatus;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReactionType;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumReactionRequest;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumCommentResponse;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumPostResponse;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumProgramResponse;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumTopicResponse;
import com.fptu.exe.skillswap.modules.forum.repository.ForumCommentReactionRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumCommentRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumPostReactionRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumPostRepository;
import com.fptu.exe.skillswap.modules.identity.domain.AcademicProgram;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import com.fptu.exe.skillswap.shared.util.UuidUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Runs lock-free reaction mutations with atomic counter updates in a dedicated transaction. */
@Service
@RequiredArgsConstructor
public class ForumReactionService {

    private final ForumPostRepository forumPostRepository;
    private final ForumCommentRepository forumCommentRepository;
    private final ForumPostReactionRepository forumPostReactionRepository;
    private final ForumCommentReactionRepository forumCommentReactionRepository;
    private final ForumActionLogService forumActionLogService;
    private final TransactionTemplate transactionTemplate;

    public ForumCommentResponse upsertCommentReaction(User currentUser, UUID commentId,
                                                       ForumReactionRequest request) {
        synchronized ((currentUser.getId() + ":c:" + commentId).intern()) {
            return transactionTemplate.execute(status -> doUpsertCommentReaction(currentUser, commentId, request));
        }
    }

    private ForumCommentResponse doUpsertCommentReaction(User currentUser, UUID commentId,
                                                          ForumReactionRequest request) {
        requireLike(request, "comment");
        ForumComment comment = forumCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bình luận forum"));
        ensureVisible(comment);

        Optional<ForumCommentReaction> existing =
                forumCommentReactionRepository.findByCommentIdAndUserId(commentId, currentUser.getId());
        if (existing.isEmpty()) {
            forumCommentReactionRepository.save(ForumCommentReaction.builder()
                    .comment(comment)
                    .user(currentUser)
                    .reactionType(ForumReactionType.LIKE)
                    .build());
            forumCommentRepository.incrementReactionCount(commentId);
        }

        int currentReactionCount = forumCommentRepository.getReactionCountById(commentId);

        forumActionLogService.record(currentUser, ForumActionType.TOGGLE_REACTION, "COMMENT", commentId,
                Map.of("reactionType", ForumReactionType.LIKE.name(), "operation", "UPSERT"));
        return toCommentResponse(comment, currentUser.getId(), currentReactionCount, true);
    }

    public ForumCommentResponse removeCommentReaction(User currentUser, UUID commentId) {
        synchronized ((currentUser.getId() + ":c:" + commentId).intern()) {
            return transactionTemplate.execute(status -> doRemoveCommentReaction(currentUser, commentId));
        }
    }

    private ForumCommentResponse doRemoveCommentReaction(User currentUser, UUID commentId) {
        ForumComment comment = forumCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bình luận forum"));
        ensureVisible(comment);

        Optional<ForumCommentReaction> existing =
                forumCommentReactionRepository.findByCommentIdAndUserId(commentId, currentUser.getId());
        if (existing.isPresent()) {
            forumCommentReactionRepository.delete(existing.get());
            forumCommentRepository.decrementReactionCount(commentId);
        }

        int currentReactionCount = forumCommentRepository.getReactionCountById(commentId);

        forumActionLogService.record(currentUser, ForumActionType.TOGGLE_REACTION, "COMMENT", commentId,
                Map.of("reactionType", ForumReactionType.LIKE.name(), "operation", "REMOVE"));
        return toCommentResponse(comment, currentUser.getId(), currentReactionCount, false);
    }

    public ForumPostResponse upsertPostReaction(User currentUser, UUID postId, ForumReactionRequest request) {
        synchronized ((currentUser.getId() + ":p:" + postId).intern()) {
            return transactionTemplate.execute(status -> doUpsertPostReaction(currentUser, postId, request));
        }
    }

    private ForumPostResponse doUpsertPostReaction(User currentUser, UUID postId, ForumReactionRequest request) {
        requireLike(request, "post");
        ForumPost post = forumPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài viết forum"));
        ensureVisible(post);

        Optional<ForumPostReaction> existing =
                forumPostReactionRepository.findByPostIdAndUserId(postId, currentUser.getId());
        if (existing.isEmpty()) {
            forumPostReactionRepository.save(ForumPostReaction.builder()
                    .post(post)
                    .user(currentUser)
                    .reactionType(ForumReactionType.LIKE)
                    .build());
            forumPostRepository.incrementReactionCount(postId);
        }

        int currentReactionCount = forumPostRepository.getReactionCountById(postId);

        forumActionLogService.record(currentUser, ForumActionType.TOGGLE_REACTION, "POST", postId,
                Map.of("reactionType", ForumReactionType.LIKE.name(), "operation", "UPSERT"));
        return toPostResponse(post, currentUser.getId(), currentReactionCount, true, ForumReactionType.LIKE);
    }

    public ForumPostResponse removePostReaction(User currentUser, UUID postId) {
        synchronized ((currentUser.getId() + ":p:" + postId).intern()) {
            return transactionTemplate.execute(status -> doRemovePostReaction(currentUser, postId));
        }
    }

    private ForumPostResponse doRemovePostReaction(User currentUser, UUID postId) {
        ForumPost post = forumPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài viết forum"));
        ensureVisible(post);

        Optional<ForumPostReaction> existing =
                forumPostReactionRepository.findByPostIdAndUserId(postId, currentUser.getId());
        if (existing.isPresent()) {
            forumPostReactionRepository.delete(existing.get());
            forumPostRepository.decrementReactionCount(postId);
        }

        int currentReactionCount = forumPostRepository.getReactionCountById(postId);

        forumActionLogService.record(currentUser, ForumActionType.TOGGLE_REACTION, "POST", postId,
                Map.of("reactionType", ForumReactionType.LIKE.name(), "operation", "REMOVE"));
        return toPostResponse(post, currentUser.getId(), currentReactionCount, false, null);
    }

    private void requireLike(ForumReactionRequest request, String target) {
        if (request == null || request.reactionType() != ForumReactionType.LIKE) {
            String suffix = "comment".equals(target) ? " cho comment" : "";
            throw new BaseException(ErrorCode.BAD_REQUEST, "Forum MVP hiện chỉ hỗ trợ reaction LIKE" + suffix);
        }
    }

    private void ensureVisible(ForumComment comment) {
        if (comment.getStatus() != ForumCommentStatus.VISIBLE) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Không thể thả reaction cho bình luận đã bị ẩn hoặc xóa");
        }
        if (comment.getPost().getStatus() != ForumPostStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Không tìm thấy bài viết forum");
        }
    }

    private void ensureVisible(ForumPost post) {
        if (post.getStatus() != ForumPostStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Không tìm thấy bài viết forum");
        }
    }

    private ForumCommentResponse toCommentResponse(ForumComment comment, UUID currentUserId, int reactionCount, boolean reacted) {
        Map<UUID, ForumComment> replyParents = loadReplyParentsById(List.of(comment));
        ForumComment replyParent = comment.getReplyToCommentId() == null
                ? null : replyParents.get(comment.getReplyToCommentId());
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
                .reactionCount(reactionCount)
                .reactedByCurrentUser(reacted)
                .replyToCommentId(comment.getReplyToCommentId())
                .replyToUserId(replyParent == null ? null : replyParent.getAuthorUser().getId())
                .replyToUserName(replyParent == null ? null : replyParent.getAuthorUser().getFullName())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .imageUrls(comment.getImageUrls())
                .build();
    }

    private ForumPostResponse toPostResponse(ForumPost post, UUID currentUserId, int reactionCount, boolean reacted, ForumReactionType reactionType) {
        ForumTopicResponse topic = ForumTopicResponse.builder()
                .id(post.getForumTopic().getId())
                .code(post.getForumTopic().getCode())
                .nameVi(post.getForumTopic().getNameVi())
                .nameEn(post.getForumTopic().getNameEn())
                .displayOrder(post.getForumTopic().getDisplayOrder())
                .build();
        return ForumPostResponse.builder()
                .postId(post.getId())
                .authorUserId(post.getAuthorUser().getId())
                .authorFullName(post.getAuthorUser().getFullName())
                .authorAvatarUrl(post.getAuthorUser().getAvatarUrl())
                .authorProgram(toProgramResponse(post.getAuthorProgram()))
                .forumTopic(topic)
                .title(post.getTitle())
                .content(post.getContent())
                .status(post.getStatus().name())
                .commentCount(defaultInt(post.getCommentCount()))
                .reactionCount(reactionCount)
                .reportCount(defaultInt(post.getReportCount()))
                .lastActivityAt(post.getLastActivityAt())
                .reactedByCurrentUser(reacted)
                .myReactionType(reactionType == null ? null : reactionType.name())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .imageUrls(post.getImageUrls())
                .build();
    }

    private Map<UUID, ForumComment> loadReplyParentsById(List<ForumComment> comments) {
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

    private ForumProgramResponse toProgramResponse(AcademicProgram program) {
        return program == null ? null : new ForumProgramResponse(
                program.getId(), program.getCode(), program.getNameVi(), program.getNameEn());
    }

    private String determineAuthorRole(Set<RoleCode> roles) {
        if (roles == null) {
            return "MENTEE";
        }
        return roles.contains(RoleCode.MENTOR) ? "MENTOR" : "MENTEE";
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }
}
