package com.fptu.exe.skillswap.modules.forum.service;

import com.fptu.exe.skillswap.modules.forum.domain.ForumComment;
import com.fptu.exe.skillswap.modules.forum.domain.ForumCommentStatus;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPost;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPostStatus;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReport;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReportStatus;
import com.fptu.exe.skillswap.modules.forum.dto.request.AdminForumCommentListRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.AdminForumPostListRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.AdminForumReportListRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumReportResolveRequest;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumCommentResponse;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumPostResponse;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumReportResponse;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPort;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.CommentListQuery;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.CommentView;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.PostListQuery;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.PostView;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.ReportListQuery;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.ReportView;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.ResolveReportCommand;
import com.fptu.exe.skillswap.modules.forum.repository.ForumCommentRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumPostReactionRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumPostRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumPostSpecification;
import com.fptu.exe.skillswap.modules.forum.repository.ForumReportRepository;
import com.fptu.exe.skillswap.modules.mentor.port.MentorViolationCommandPort;
import com.fptu.exe.skillswap.modules.notification.NotificationType;
import com.fptu.exe.skillswap.modules.notification.port.NotificationCommandPort;
import com.fptu.exe.skillswap.modules.notification.port.NotificationCommandPort.NotificationIntent;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.cursor.CursorCodec;
import com.fptu.exe.skillswap.shared.cursor.CursorTokenPayload;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForumAdminPortImpl implements ForumAdminPort {

    private static final String PARENT_COMMENT_HIDDEN_REASON_PREFIX = "Parent comment hidden";

    private final ForumPostRepository forumPostRepository;
    private final ForumCommentRepository forumCommentRepository;
    private final ForumPostReactionRepository forumPostReactionRepository;
    private final ForumReportRepository forumReportRepository;
    private final NotificationCommandPort notificationCommandPort;
    private final ForumTextPolicy forumTextPolicy;
    private final CursorCodec cursorCodec;
    private MentorViolationCommandPort mentorViolationCommandPort;

    @Autowired(required = false)
    void setMentorViolationCommandPort(MentorViolationCommandPort mentorViolationCommandPort) {
        this.mentorViolationCommandPort = mentorViolationCommandPort;
    }

    @Override
    public List<String> reportStatusNames() {
        return java.util.Arrays.stream(ForumReportStatus.values()).map(Enum::name).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReportView> getReports(ReportListQuery query) {
        AdminForumReportListRequest request = reportListRequest(query);
        Pageable pageable = PageRequest.of(defaultPage(request.page()), defaultSize(request.size()), Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<ForumReport> reports = forumReportRepository.searchReports(
                request.status(),
                request.targetType(),
                toKeywordPattern(request.keyword()),
                pageable
        );
        Map<UUID, ForumPost> postsById = loadReportedPosts(reports.getContent());
        Map<UUID, ForumComment> commentsById = loadReportedComments(reports.getContent());
        Page<ReportView> page = reports.map(report -> reportView(toReportResponse(report, postsById, commentsById)));
        return toReportPageResponse(page);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportView getReportDetail(UUID reportId) {
        ForumReport report = forumReportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy forum report"));
        return reportView(toReportResponse(report, Collections.emptyMap(), Collections.emptyMap()));
    }

    @Override
    @Transactional
    public ReportView resolveReport(UUID adminUserId, UUID reportId, ResolveReportCommand command) {
        ForumReportResolveRequest request = resolveRequest(command);
        ForumReport report = forumReportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy forum report"));
        if (report.getStatus() != ForumReportStatus.OPEN) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Forum report này đã được xử lý trước đó");
        }
        if (request.action() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "action là bắt buộc");
        }

        switch (request.action()) {
            case CONFIRM_NO_ACTION -> confirmNoAction(adminUserId, report, request.reviewNote());
            case HIDE_POST -> hideReportedPost(adminUserId, report, request.reviewNote());
            case HIDE_COMMENT -> hideReportedComment(adminUserId, report, request.reviewNote());
            case DISMISS -> dismissReport(adminUserId, report, request.reviewNote());
            default -> throw new BaseException(ErrorCode.BAD_REQUEST, "Action moderation không hợp lệ");
        }

        return reportView(toReportResponse(report, Collections.emptyMap(), Collections.emptyMap()));
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<PostView> getAdminPosts(PostListQuery query) {
        AdminForumPostListRequest request = postListRequest(query);
        int resolvedLimit = defaultLimit(request.limit());
        String filterHash = buildAdminPostFilterHash(request);
        DecodedPostCursor decodedCursor = decodePostCursor(request.cursor(), filterHash);
        Specification<ForumPost> specification = buildAdminPostSpecification(request, decodedCursor);
        List<ForumPost> postWindow = forumPostRepository.findWindow(specification, resolvedLimit + 1);
        boolean hasNext = postWindow.size() > resolvedLimit;
        List<ForumPost> visiblePosts = hasNext ? new ArrayList<>(postWindow.subList(0, resolvedLimit)) : postWindow;
        List<PostView> items = visiblePosts.stream()
                .map(post -> postView(toPostResponse(post, null)))
                .toList();
        String nextCursor = hasNext && !visiblePosts.isEmpty()
                ? encodeNextCursor(visiblePosts.get(visiblePosts.size() - 1), filterHash)
                : null;
        return CursorPageResponse.<PostView>builder()
                .items(items)
                .nextCursor(nextCursor)
                .prevCursor(null)
                .hasNext(hasNext)
                .hasPrev(false)
                .limit(resolvedLimit)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<CommentView> getAdminComments(CommentListQuery query) {
        AdminForumCommentListRequest request = commentListRequest(query);
        int resolvedLimit = defaultLimit(request.limit());
        String filterHash = buildAdminCommentFilterHash(request);
        DecodedCommentCursor decodedCursor = decodeCommentCursor(request.cursor(), filterHash);
        List<ForumComment> commentWindow = forumCommentRepository.findAdminCommentsWindow(
                request.status(),
                request.postId(),
                request.authorId(),
                toKeywordPattern(request.keyword()),
                decodedCursor.createdAt(),
                decodedCursor.commentId(),
                resolvedLimit + 1
        );
        boolean hasNext = commentWindow.size() > resolvedLimit;
        List<ForumComment> visibleComments = hasNext ? new ArrayList<>(commentWindow.subList(0, resolvedLimit)) : commentWindow;
        Map<UUID, ForumComment> replyParentsById = loadReplyParentsById(visibleComments);
        List<CommentView> items = visibleComments.stream()
                .map(comment -> commentView(toCommentResponse(comment, replyParentsById)))
                .toList();
        String nextCursor = hasNext && !visibleComments.isEmpty()
                ? encodeNextCommentCursor(visibleComments.get(visibleComments.size() - 1), filterHash)
                : null;
        return CursorPageResponse.<CommentView>builder()
                .items(items)
                .nextCursor(nextCursor)
                .prevCursor(null)
                .hasNext(hasNext)
                .hasPrev(false)
                .limit(resolvedLimit)
                .build();
    }

    @Override
    @Transactional
    public PostView restorePost(UUID adminUserId, UUID postId) {
        ForumPost post = forumPostRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài viết forum"));
        if (post.getStatus() == ForumPostStatus.PUBLISHED) {
            return postView(toPostResponse(post, null));
        }
        post.setStatus(ForumPostStatus.PUBLISHED);
        post.setHiddenAt(null);
        post.setHiddenByUserId(null);
        post.setHiddenReason(null);
        ForumPost saved = forumPostRepository.save(post);
        return postView(toPostResponse(saved, null));
    }

    @Override
    @Transactional
    public CommentView restoreComment(UUID adminUserId, UUID commentId) {
        ForumComment comment = forumCommentRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bình luận forum"));
        if (comment.getStatus() == ForumCommentStatus.VISIBLE) {
            return commentView(toCommentResponse(comment));
        }
        comment.setStatus(ForumCommentStatus.VISIBLE);
        comment.setHiddenAt(null);
        comment.setHiddenByUserId(null);
        comment.setHiddenReason(null);
        ForumComment saved = forumCommentRepository.save(comment);
        List<ForumComment> restoredReplies = restoreRepliesHiddenWithParent(comment);
        ensureReplyParentVisible(saved);
        ForumPost post = forumPostRepository.findByIdForUpdate(saved.getPost().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài viết forum"));
        post.setCommentCount((post.getCommentCount() == null ? 0 : post.getCommentCount()) + 1 + restoredReplies.size());
        forumPostRepository.save(post);
        return commentView(toCommentResponse(saved));
    }

    @Override
    public long countPendingReports() {
        return forumReportRepository.countByStatus(ForumReportStatus.OPEN);
    }

    @Override
    public long countReportsCreatedBy(UUID userId) {
        return userId == null ? 0 : forumReportRepository.countCreatedByReporterUserId(userId);
    }

    private void confirmNoAction(UUID adminUserId, ForumReport report, String reviewNote) {
        report.setStatus(ForumReportStatus.RESOLVED_NO_ACTION);
        report.setReviewedByUserId(adminUserId);
        report.setReviewNote(cleanNote(reviewNote));
        report.setResolvedAt(DateTimeUtil.now());
        forumReportRepository.save(report);
    }

    private void hideReportedPost(UUID adminUserId, ForumReport report, String reviewNote) {
        if (report.getTargetType() != com.fptu.exe.skillswap.modules.forum.domain.ForumReportTargetType.POST) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Report này không trỏ tới bài viết");
        }
        ForumPost post = forumPostRepository.findByIdForUpdate(report.getTargetId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài viết forum"));
        if (post.getStatus() == ForumPostStatus.HIDDEN) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Bài viết này đã bị ẩn trước đó");
        }
        post.setStatus(ForumPostStatus.HIDDEN);
        post.setHiddenAt(DateTimeUtil.now());
        post.setHiddenByUserId(adminUserId);
        post.setHiddenReason(cleanNote(reviewNote));
        forumPostRepository.save(post);

        report.setStatus(ForumReportStatus.RESOLVED_ACTION_TAKEN);
        report.setReviewedByUserId(adminUserId);
        report.setReviewNote(cleanNote(reviewNote));
        report.setResolvedAt(DateTimeUtil.now());
        forumReportRepository.save(report);

        recordForumViolationIfMentor(post.getAuthorUser().getId(), post.getId(), adminUserId, reviewNote);

        notifyBestEffort(
                post.getAuthorUser().getId(),
                NotificationType.FORUM_POST_HIDDEN,
                "Bài viết forum của bạn đã bị ẩn",
                "Một bài viết forum của bạn đã bị admin ẩn do vi phạm quy định nội dung.",
                "FORUM_POST",
                post.getId()
        );
    }

    private void hideReportedComment(UUID adminUserId, ForumReport report, String reviewNote) {
        if (report.getTargetType() != com.fptu.exe.skillswap.modules.forum.domain.ForumReportTargetType.COMMENT) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Report này không trỏ tới bình luận");
        }
        ForumComment comment = forumCommentRepository.findByIdForUpdate(report.getTargetId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bình luận forum"));
        if (comment.getStatus() == ForumCommentStatus.HIDDEN) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Bình luận này đã bị ẩn trước đó");
        }
        comment.setStatus(ForumCommentStatus.HIDDEN);
        comment.setHiddenAt(DateTimeUtil.now());
        comment.setHiddenByUserId(adminUserId);
        comment.setHiddenReason(cleanNote(reviewNote));
        forumCommentRepository.save(comment);
        List<ForumComment> visibleReplies = forumCommentRepository.findByReplyToCommentIdAndStatus(comment.getId(), ForumCommentStatus.VISIBLE);
        String replyHiddenReason = childHiddenReason(reviewNote);
        visibleReplies.forEach(reply -> {
            reply.setStatus(ForumCommentStatus.HIDDEN);
            reply.setHiddenAt(DateTimeUtil.now());
            reply.setHiddenByUserId(adminUserId);
            reply.setHiddenReason(replyHiddenReason);
            forumCommentRepository.save(reply);
        });

        ForumPost post = forumPostRepository.findByIdForUpdate(comment.getPost().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài viết forum"));
        if (post.getCommentCount() != null && post.getCommentCount() > 0) {
            post.setCommentCount(Math.max(0, post.getCommentCount() - 1 - visibleReplies.size()));
            forumPostRepository.save(post);
        }

        report.setStatus(ForumReportStatus.RESOLVED_ACTION_TAKEN);
        report.setReviewedByUserId(adminUserId);
        report.setReviewNote(cleanNote(reviewNote));
        report.setResolvedAt(DateTimeUtil.now());
        forumReportRepository.save(report);

        recordForumViolationIfMentor(comment.getAuthorUser().getId(), comment.getId(), adminUserId, reviewNote);

        notifyBestEffort(
                comment.getAuthorUser().getId(),
                NotificationType.FORUM_COMMENT_HIDDEN,
                "Bình luận forum của bạn đã bị ẩn",
                "Một bình luận forum của bạn đã bị admin ẩn do vi phạm quy định nội dung.",
                "FORUM_COMMENT",
                comment.getId()
        );
    }

    private void recordForumViolationIfMentor(UUID authorUserId, UUID sourceReferenceId, UUID adminUserId, String reviewNote) {
        if (mentorViolationCommandPort == null || authorUserId == null) {
            return;
        }
        mentorViolationCommandPort.recordConfirmedViolation(new MentorViolationCommandPort.MentorViolationCommand(
                authorUserId, "FORUM", sourceReferenceId, "FORUM_POLICY_BREACH", "MEDIUM", adminUserId,
                "Admin ẩn nội dung forum của mentor do vi phạm quy định.", reviewNote));
    }

    private void dismissReport(UUID adminUserId, ForumReport report, String reviewNote) {
        report.setStatus(ForumReportStatus.DISMISSED);
        report.setReviewedByUserId(adminUserId);
        report.setReviewNote(cleanNote(reviewNote));
        report.setResolvedAt(DateTimeUtil.now());
        forumReportRepository.save(report);
    }

    private ForumReportResponse toReportResponse(ForumReport report,
                                                 Map<UUID, ForumPost> postsById,
                                                 Map<UUID, ForumComment> commentsById) {
        String targetStatus = null;
        String targetTitle = null;
        String targetContentPreview = null;
        UUID targetAuthorUserId = null;
        String targetAuthorFullName = null;

        if (report.getTargetType() == com.fptu.exe.skillswap.modules.forum.domain.ForumReportTargetType.POST) {
            ForumPost post = postsById.get(report.getTargetId());
            if (post == null) {
                post = forumPostRepository.findById(report.getTargetId()).orElse(null);
            }
            if (post != null) {
                targetStatus = post.getStatus().name();
                targetTitle = post.getTitle();
                targetContentPreview = trimPreview(post.getContent());
                targetAuthorUserId = post.getAuthorUser().getId();
                targetAuthorFullName = post.getAuthorUser().getFullName();
            }
        } else if (report.getTargetType() == com.fptu.exe.skillswap.modules.forum.domain.ForumReportTargetType.COMMENT) {
            ForumComment comment = commentsById.get(report.getTargetId());
            if (comment == null) {
                comment = forumCommentRepository.findById(report.getTargetId()).orElse(null);
            }
            if (comment != null) {
                targetStatus = comment.getStatus().name();
                targetContentPreview = trimPreview(comment.getContent());
                targetAuthorUserId = comment.getAuthorUser().getId();
                targetAuthorFullName = comment.getAuthorUser().getFullName();
            }
        }

        return ForumReportResponse.builder()
                .reportId(report.getId())
                .targetType(report.getTargetType().name())
                .targetId(report.getTargetId())
                .targetStatus(targetStatus)
                .targetTitle(targetTitle)
                .targetContentPreview(targetContentPreview)
                .targetAuthorUserId(targetAuthorUserId)
                .targetAuthorFullName(targetAuthorFullName)
                .reporterUserId(report.getReporterUser().getId())
                .reporterFullName(report.getReporterUser().getFullName())
                .reasonType(report.getReasonType().name())
                .description(report.getDescription())
                .status(report.getStatus().name())
                .reviewedByUserId(report.getReviewedByUserId())
                .reviewNote(report.getReviewNote())
                .resolvedAt(report.getResolvedAt())
                .createdAt(report.getCreatedAt())
                .build();
    }

    private Map<UUID, ForumPost> loadReportedPosts(Collection<ForumReport> reports) {
        List<UUID> postIds = reports.stream()
                .filter(report -> report.getTargetType() == com.fptu.exe.skillswap.modules.forum.domain.ForumReportTargetType.POST)
                .map(ForumReport::getTargetId)
                .distinct()
                .toList();
        if (postIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ForumPost> postsById = new HashMap<>();
        forumPostRepository.findByIdIn(postIds).forEach(post -> postsById.put(post.getId(), post));
        return postsById;
    }

    private Map<UUID, ForumComment> loadReportedComments(Collection<ForumReport> reports) {
        List<UUID> commentIds = reports.stream()
                .filter(report -> report.getTargetType() == com.fptu.exe.skillswap.modules.forum.domain.ForumReportTargetType.COMMENT)
                .map(ForumReport::getTargetId)
                .distinct()
                .toList();
        if (commentIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ForumComment> commentsById = new HashMap<>();
        forumCommentRepository.findByIdIn(commentIds).forEach(comment -> commentsById.put(comment.getId(), comment));
        return commentsById;
    }

    private ForumPostResponse toPostResponse(ForumPost post, UUID currentUserId) {
        String myReactionType = null;
        boolean reactedByCurrentUser = false;
        if (currentUserId != null) {
            var reaction = forumPostReactionRepository.findByPostIdAndUserId(post.getId(), currentUserId);
            reactedByCurrentUser = reaction.isPresent();
            myReactionType = reaction.map(value -> value.getReactionType().name()).orElse(null);
        }
        return ForumPostResponse.builder()
                .postId(post.getId())
                .authorUserId(post.getAuthorUser().getId())
                .authorFullName(post.getAuthorUser().getFullName())
                .authorAvatarUrl(post.getAuthorUser().getAvatarUrl())
                .forumTopic(com.fptu.exe.skillswap.modules.forum.dto.response.ForumTopicResponse.builder()
                        .id(post.getForumTopic().getId())
                        .code(post.getForumTopic().getCode())
                        .nameVi(post.getForumTopic().getNameVi())
                        .nameEn(post.getForumTopic().getNameEn())
                        .displayOrder(post.getForumTopic().getDisplayOrder())
                        .build())
                .title(post.getTitle())
                .content(post.getContent())
                .status(post.getStatus().name())
                .commentCount(post.getCommentCount() == null ? 0 : post.getCommentCount())
                .reactionCount(post.getReactionCount() == null ? 0 : post.getReactionCount())
                .reportCount(post.getReportCount() == null ? 0 : post.getReportCount())
                .lastActivityAt(post.getLastActivityAt())
                .reactedByCurrentUser(reactedByCurrentUser)
                .myReactionType(myReactionType)
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .imageUrls(post.getImageUrls())
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
        return toCommentResponse(comment, loadReplyParentsById(List.of(comment)));
    }

    private ForumCommentResponse toCommentResponse(ForumComment comment, Map<UUID, ForumComment> replyParentsById) {
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
                .reportCount(comment.getReportCount() == null ? 0 : comment.getReportCount())
                .reactionCount(comment.getReactionCount() == null ? 0 : comment.getReactionCount())
                .reactedByCurrentUser(false)
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

    private PageResponse<ReportView> toReportPageResponse(Page<ReportView> page) {
        return PageResponse.<ReportView>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    private AdminForumReportListRequest reportListRequest(ReportListQuery query) {
        if (query == null) {
            return new AdminForumReportListRequest(null, null, null, null, null);
        }
        return new AdminForumReportListRequest(query.page(), query.size(), query.keyword(),
                enumValue(ForumReportStatus.class, query.status()),
                enumValue(com.fptu.exe.skillswap.modules.forum.domain.ForumReportTargetType.class, query.targetType()));
    }

    private AdminForumPostListRequest postListRequest(PostListQuery query) {
        if (query == null) {
            return new AdminForumPostListRequest(null, null, null, null, null, null);
        }
        return new AdminForumPostListRequest(query.cursor(), query.limit(), query.keyword(), query.forumTopicId(),
                query.authorId(), enumValue(ForumPostStatus.class, query.status()));
    }

    private AdminForumCommentListRequest commentListRequest(CommentListQuery query) {
        if (query == null) {
            return new AdminForumCommentListRequest(null, null, null, null, null, null);
        }
        return new AdminForumCommentListRequest(query.cursor(), query.limit(), query.keyword(), query.postId(),
                query.authorId(), enumValue(ForumCommentStatus.class, query.status()));
    }

    private ForumReportResolveRequest resolveRequest(ResolveReportCommand command) {
        if (command == null) {
            return new ForumReportResolveRequest(null, null);
        }
        return new ForumReportResolveRequest(
                enumValue(com.fptu.exe.skillswap.modules.forum.domain.ForumModerationAction.class, command.action()),
                command.reviewNote());
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Giá trị moderation forum không hợp lệ");
        }
    }

    private ReportView reportView(ForumReportResponse value) {
        return new ReportView(value.reportId(), value.targetType(), value.targetId(), value.targetStatus(), value.targetTitle(),
                value.targetContentPreview(), value.targetAuthorUserId(), value.targetAuthorFullName(), value.reporterUserId(),
                value.reporterFullName(), value.reasonType(), value.description(), value.status(), value.reviewedByUserId(),
                value.reviewNote(), value.resolvedAt(), value.createdAt());
    }

    private PostView postView(ForumPostResponse value) {
        var program = value.authorProgram();
        var topic = value.forumTopic();
        return new PostView(value.postId(), value.authorUserId(), value.authorFullName(), value.authorAvatarUrl(),
                program == null ? null : new com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.ProgramView(
                        program.id(), program.code(), program.nameVi(), program.nameEn()),
                topic == null ? null : new com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.TopicView(
                        topic.id(), topic.code() == null ? null : topic.code().name(), topic.nameVi(), topic.nameEn(), topic.displayOrder()),
                value.title(), value.content(), value.status(), value.commentCount(), value.reactionCount(), value.reportCount(),
                value.lastActivityAt(), value.reactedByCurrentUser(), value.myReactionType(), value.createdAt(), value.updatedAt(), value.imageUrls());
    }

    private CommentView commentView(ForumCommentResponse value) {
        return new CommentView(value.commentId(), value.postId(), value.authorUserId(), value.authorFullName(), value.authorAvatarUrl(),
                value.authorRole(), value.content(), value.status(), value.reportCount(), value.reactionCount(),
                value.reactedByCurrentUser(), value.replyToCommentId(), value.replyToUserId(), value.replyToUserName(),
                value.createdAt(), value.updatedAt(), value.imageUrls());
    }

    private String trimPreview(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 160);
    }

    private String cleanNote(String note) {
        return forumTextPolicy.normalizeOptionalPlainText(note, "Ghi chú moderation");
    }

    private String childHiddenReason(String parentNote) {
        String cleaned = cleanNote(parentNote);
        String reason = cleaned == null ? PARENT_COMMENT_HIDDEN_REASON_PREFIX : PARENT_COMMENT_HIDDEN_REASON_PREFIX + ": " + cleaned;
        return reason.length() <= 500 ? reason : reason.substring(0, 500);
    }

    private List<ForumComment> restoreRepliesHiddenWithParent(ForumComment comment) {
        if (comment.getReplyToCommentId() != null) {
            return List.of();
        }
        List<ForumComment> hiddenReplies = forumCommentRepository.findByReplyToCommentIdAndStatus(comment.getId(), ForumCommentStatus.HIDDEN);
        List<ForumComment> repliesToRestore = hiddenReplies.stream()
                .filter(reply -> reply.getHiddenReason() != null && reply.getHiddenReason().startsWith(PARENT_COMMENT_HIDDEN_REASON_PREFIX))
                .toList();
        repliesToRestore.forEach(reply -> {
            reply.setStatus(ForumCommentStatus.VISIBLE);
            reply.setHiddenAt(null);
            reply.setHiddenByUserId(null);
            reply.setHiddenReason(null);
            forumCommentRepository.save(reply);
        });
        return repliesToRestore;
    }

    private void ensureReplyParentVisible(ForumComment comment) {
        if (comment.getReplyToCommentId() == null) {
            return;
        }
        ForumComment parent = forumCommentRepository.findById(comment.getReplyToCommentId())
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_CONFLICT, "Không thể khôi phục reply khi bình luận gốc không tồn tại"));
        if (parent.getStatus() != ForumCommentStatus.VISIBLE) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Cần khôi phục bình luận gốc trước khi khôi phục reply");
        }
    }

    private void notifyBestEffort(UUID recipientUserId,
                                  NotificationType type,
                                  String title,
                                  String message,
                                  String relatedEntityType,
                                  UUID relatedEntityId) {
        try {
            notificationCommandPort.publish(new NotificationIntent(
                    recipientUserId,
                    type.name(),
                    title,
                    message,
                    relatedEntityType,
                    relatedEntityId,
                    null
            ));
        } catch (RuntimeException ex) {
            log.warn("Không thể tạo notification forum moderation recipientUserId={} type={} relatedEntityId={}: {}",
                    recipientUserId,
                    type,
                    relatedEntityId,
                    ex.getMessage());
        }
    }

    private int defaultPage(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    private int defaultSize(Integer size) {
        int resolved = size == null || size <= 0 ? 20 : size;
        return Math.min(resolved, 50);
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

    private String buildAdminPostFilterHash(AdminForumPostListRequest request) {
        return "forum-posts:admin|keyword=" + normalizeKeywordFilterValue(request.keyword())
                + "|forumTopicId=" + normalizeFilterValue(request.forumTopicId())
                + "|authorId=" + normalizeFilterValue(request.authorId())
                + "|status=" + normalizeFilterValue(request.status());
    }

    private String buildAdminCommentFilterHash(AdminForumCommentListRequest request) {
        return "forum-comments:admin|keyword=" + normalizeKeywordFilterValue(request.keyword())
                + "|postId=" + normalizeFilterValue(request.postId())
                + "|authorId=" + normalizeFilterValue(request.authorId())
                + "|status=" + normalizeFilterValue(request.status());
    }

    private Specification<ForumPost> buildAdminPostSpecification(AdminForumPostListRequest request,
                                                                 DecodedPostCursor decodedCursor) {
        return Specification.where(ForumPostSpecification.hasStatus(request.status()))
                .and(ForumPostSpecification.hasForumTopic(request.forumTopicId()))
                .and(ForumPostSpecification.hasAuthor(request.authorId()))
                .and(ForumPostSpecification.hasKeyword(toKeywordPattern(request.keyword())))
                .and(ForumPostSpecification.isBeforeCursor(decodedCursor.lastActivityAt(), decodedCursor.postId()));
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

    private DecodedCommentCursor decodeCommentCursor(String cursor, String expectedFilterHash) {
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
        return new DecodedCommentCursor(parseCursorDateTime(payload.sortKey()), parseCursorUuid(payload.secondaryKey(), "comment"));
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

    @Override
    @Transactional(readOnly = true)
    public boolean existsReportById(UUID reportId) {
        if (reportId == null) {
            return false;
        }
        return forumReportRepository.existsById(reportId);
    }
}
