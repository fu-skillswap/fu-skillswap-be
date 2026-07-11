package com.fptu.exe.skillswap.modules.forum.service;

import com.fptu.exe.skillswap.modules.forum.domain.ForumComment;
import com.fptu.exe.skillswap.modules.forum.domain.ForumActionType;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPost;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReport;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReportStatus;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumReportCreateRequest;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumReportResponse;
import com.fptu.exe.skillswap.modules.forum.repository.ForumCommentRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumPostRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumReportRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ForumReportService {

    private final ForumPostService forumPostService;
    private final ForumPostRepository forumPostRepository;
    private final ForumCommentRepository forumCommentRepository;
    private final ForumReportRepository forumReportRepository;
    private final ForumTextPolicy forumTextPolicy;
    private final ForumAbuseGuardService forumAbuseGuardService;

    @Transactional
    public ForumReportResponse createReport(UUID currentUserId, ForumReportCreateRequest request) {
        User reporter = forumPostService.requireForumUser(currentUserId);
        forumAbuseGuardService.checkAndLog(reporter, ForumActionType.CREATE_REPORT);
        if (forumReportRepository.existsByReporterUserIdAndTargetTypeAndTargetId(
                reporter.getId(),
                request.targetType(),
                request.targetId()
        )) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Bạn đã report nội dung này trước đó");
        }

        String targetStatus;
        String targetTitle;
        String targetPreview;
        UUID targetAuthorId;
        String targetAuthorName;

        switch (request.targetType()) {
            case POST -> {
                ForumPost post = forumPostService.requireVisiblePost(request.targetId());
                if (post.getAuthorUser().getId().equals(reporter.getId())) {
                    throw new BaseException(ErrorCode.BAD_REQUEST, "Bạn không thể report bài viết của chính mình");
                }
                post.setReportCount((post.getReportCount() == null ? 0 : post.getReportCount()) + 1);
                forumPostRepository.save(post);
                targetStatus = post.getStatus().name();
                targetTitle = post.getTitle();
                targetPreview = trimPreview(post.getContent());
                targetAuthorId = post.getAuthorUser().getId();
                targetAuthorName = post.getAuthorUser().getFullName();
            }
            case COMMENT -> {
                ForumComment comment = forumPostService.requireVisibleComment(request.targetId());
                if (comment.getAuthorUser().getId().equals(reporter.getId())) {
                    throw new BaseException(ErrorCode.BAD_REQUEST, "Bạn không thể report bình luận của chính mình");
                }
                comment.setReportCount((comment.getReportCount() == null ? 0 : comment.getReportCount()) + 1);
                forumCommentRepository.save(comment);
                targetStatus = comment.getStatus().name();
                targetTitle = null;
                targetPreview = trimPreview(comment.getContent());
                targetAuthorId = comment.getAuthorUser().getId();
                targetAuthorName = comment.getAuthorUser().getFullName();
            }
            default -> throw new BaseException(ErrorCode.BAD_REQUEST, "Loại target report không được hỗ trợ");
        }

        ForumReport report;
        try {
            report = forumReportRepository.saveAndFlush(ForumReport.builder()
                    .reporterUser(reporter)
                    .targetType(request.targetType())
                    .targetId(request.targetId())
                    .reasonType(request.reasonType())
                    .description(forumTextPolicy.normalizeOptionalPlainText(request.description(), "Mô tả report"))
                    .status(ForumReportStatus.OPEN)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Bạn đã report nội dung này trước đó", ex);
        }

        return ForumReportResponse.builder()
                .reportId(report.getId())
                .targetType(report.getTargetType().name())
                .targetId(report.getTargetId())
                .targetStatus(targetStatus)
                .targetTitle(targetTitle)
                .targetContentPreview(targetPreview)
                .targetAuthorUserId(targetAuthorId)
                .targetAuthorFullName(targetAuthorName)
                .reporterUserId(reporter.getId())
                .reporterFullName(reporter.getFullName())
                .reasonType(report.getReasonType().name())
                .description(report.getDescription())
                .status(report.getStatus().name())
                .createdAt(report.getCreatedAt())
                .build();
    }

    private String trimPreview(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 160);
    }
}
