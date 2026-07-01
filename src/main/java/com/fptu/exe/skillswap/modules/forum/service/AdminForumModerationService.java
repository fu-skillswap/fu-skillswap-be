package com.fptu.exe.skillswap.modules.forum.service;

import com.fptu.exe.skillswap.modules.forum.domain.ForumComment;
import com.fptu.exe.skillswap.modules.forum.domain.ForumCommentStatus;
import com.fptu.exe.skillswap.modules.forum.domain.ForumModerationAction;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPost;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPostStatus;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReport;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReportStatus;
import com.fptu.exe.skillswap.modules.forum.dto.request.AdminForumCommentListRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.AdminForumPostListRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.AdminForumReportListRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumReportResolveRequest;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumCommentResponse;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumHelpTopicResponse;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumPostResponse;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumReportResponse;
import com.fptu.exe.skillswap.modules.forum.repository.ForumCommentRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumPostReactionRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumPostRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumReportRepository;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminForumModerationService {

    private final ForumPostRepository forumPostRepository;
    private final ForumCommentRepository forumCommentRepository;
    private final ForumPostReactionRepository forumPostReactionRepository;
    private final ForumReportRepository forumReportRepository;
    private final NotificationService notificationService;
    private final ForumTextPolicy forumTextPolicy;

    @Transactional(readOnly = true)
    public PageResponse<ForumReportResponse> getReports(AdminForumReportListRequest request) {
        Pageable pageable = PageRequest.of(defaultPage(request.page()), defaultSize(request.size()), Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<ForumReport> reports = forumReportRepository.searchReports(
                request.status(),
                request.targetType(),
                toKeywordPattern(request.keyword()),
                pageable
        );
        Map<UUID, ForumPost> postsById = loadReportedPosts(reports.getContent());
        Map<UUID, ForumComment> commentsById = loadReportedComments(reports.getContent());
        Page<ForumReportResponse> page = reports.map(report -> toReportResponse(report, postsById, commentsById));
        return toReportPageResponse(page);
    }

    @Transactional(readOnly = true)
    public ForumReportResponse getReportDetail(UUID reportId) {
        ForumReport report = forumReportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy forum report"));
        return toReportResponse(report, Collections.emptyMap(), Collections.emptyMap());
    }

    @Transactional
    public ForumReportResponse resolveReport(UUID adminUserId, UUID reportId, ForumReportResolveRequest request) {
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
        return toReportResponse(report, Collections.emptyMap(), Collections.emptyMap());
    }

    @Transactional(readOnly = true)
    public PageResponse<ForumPostResponse> getAdminPosts(AdminForumPostListRequest request) {
        Pageable pageable = PageRequest.of(defaultPage(request.page()), defaultSize(request.size()), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ForumPostResponse> page = forumPostRepository.searchAdminPosts(
                request.status(),
                request.helpTopicId(),
                request.authorId(),
                toKeywordPattern(request.keyword()),
                pageable
        ).map(post -> toPostResponse(post, null));
        return toPostPageResponse(page);
    }

    @Transactional(readOnly = true)
    public PageResponse<ForumCommentResponse> getAdminComments(AdminForumCommentListRequest request) {
        Pageable pageable = PageRequest.of(defaultPage(request.page()), defaultSize(request.size()), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ForumCommentResponse> page = forumCommentRepository.searchAdminComments(
                request.status(),
                request.postId(),
                request.authorId(),
                toKeywordPattern(request.keyword()),
                pageable
        ).map(this::toCommentResponse);
        return toCommentPageResponse(page);
    }

    @Transactional
    public ForumPostResponse restorePost(UUID postId) {
        ForumPost post = forumPostRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài viết forum"));
        if (post.getStatus() == ForumPostStatus.PUBLISHED) {
            return toPostResponse(post, null);
        }
        post.setStatus(ForumPostStatus.PUBLISHED);
        post.setHiddenAt(null);
        post.setHiddenByUserId(null);
        post.setHiddenReason(null);
        return toPostResponse(forumPostRepository.save(post), null);
    }

    @Transactional
    public ForumCommentResponse restoreComment(UUID commentId) {
        ForumComment comment = forumCommentRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bình luận forum"));
        if (comment.getStatus() == ForumCommentStatus.VISIBLE) {
            return toCommentResponse(comment);
        }
        comment.setStatus(ForumCommentStatus.VISIBLE);
        comment.setHiddenAt(null);
        comment.setHiddenByUserId(null);
        comment.setHiddenReason(null);
        ForumComment saved = forumCommentRepository.save(comment);
        ForumPost post = forumPostRepository.findByIdForUpdate(saved.getPost().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài viết forum"));
        post.setCommentCount((post.getCommentCount() == null ? 0 : post.getCommentCount()) + 1);
        forumPostRepository.save(post);
        return toCommentResponse(saved);
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

        notificationService.createNotification(
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

        ForumPost post = forumPostRepository.findByIdForUpdate(comment.getPost().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài viết forum"));
        if (post.getCommentCount() != null && post.getCommentCount() > 0) {
            post.setCommentCount(post.getCommentCount() - 1);
            forumPostRepository.save(post);
        }

        report.setStatus(ForumReportStatus.RESOLVED_ACTION_TAKEN);
        report.setReviewedByUserId(adminUserId);
        report.setReviewNote(cleanNote(reviewNote));
        report.setResolvedAt(DateTimeUtil.now());
        forumReportRepository.save(report);

        notificationService.createNotification(
                comment.getAuthorUser().getId(),
                NotificationType.FORUM_COMMENT_HIDDEN,
                "Bình luận forum của bạn đã bị ẩn",
                "Một bình luận forum của bạn đã bị admin ẩn do vi phạm quy định nội dung.",
                "FORUM_COMMENT",
                comment.getId()
        );
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
                .helpTopic(ForumHelpTopicResponse.builder()
                        .id(post.getHelpTopic().getId())
                        .code(post.getHelpTopic().getCode())
                        .nameVi(post.getHelpTopic().getNameVi())
                        .nameEn(post.getHelpTopic().getNameEn())
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
                .reportCount(comment.getReportCount() == null ? 0 : comment.getReportCount())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }

    private PageResponse<ForumReportResponse> toReportPageResponse(Page<ForumReportResponse> page) {
        return PageResponse.<ForumReportResponse>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
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
}
