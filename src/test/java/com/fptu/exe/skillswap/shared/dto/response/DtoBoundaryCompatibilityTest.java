package com.fptu.exe.skillswap.shared.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminForumCommentResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminForumPostResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminForumReportResponse;
import com.fptu.exe.skillswap.modules.filestorage.dto.response.InternalStorageUploadResponse;
import com.fptu.exe.skillswap.modules.filestorage.dto.response.PresignedUploadResponse;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumCommentResponse;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumPostResponse;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumProgramResponse;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumReportResponse;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumTopicResponse;
import com.fptu.exe.skillswap.modules.forum.domain.ForumTopicCode;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.payment.dto.response.InternalPaymentWebhookResponse;
import com.fptu.exe.skillswap.modules.payment.dto.response.PaymentCheckoutResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DtoBoundaryCompatibilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void adminForumPostAndCommentBoundariesKeepExistingJsonNames() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        ForumProgramResponse publicProgram = new ForumProgramResponse(UUID.randomUUID(), "PROGRAM", "Chương trình", "Program");
        ForumTopicResponse publicTopic = new ForumTopicResponse(UUID.randomUUID(), ForumTopicCode.QUESTION, "Hỏi đáp", "Question", 1);

        ForumPostResponse publicPost = ForumPostResponse.builder()
                .postId(postId).authorUserId(UUID.randomUUID()).authorFullName("User").authorAvatarUrl("avatar")
                .authorProgram(publicProgram).forumTopic(publicTopic).title("Title").content("Content")
                .status("PUBLISHED").commentCount(2).reactionCount(3).reportCount(1)
                .lastActivityAt(LocalDateTime.of(2026, 9, 4, 10, 0)).reactedByCurrentUser(true).myReactionType("LIKE")
                .createdAt(LocalDateTime.of(2026, 9, 4, 9, 0)).updatedAt(LocalDateTime.of(2026, 9, 4, 9, 30))
                .imageUrls(List.of("image"))
                .build();
        AdminForumPostResponse adminPost = new AdminForumPostResponse(
                publicPost.postId(), publicPost.authorUserId(), publicPost.authorFullName(), publicPost.authorAvatarUrl(),
                new com.fptu.exe.skillswap.modules.admin.dto.response.AdminForumProgramResponse(
                        publicProgram.id(), publicProgram.code(), publicProgram.nameVi(), publicProgram.nameEn()),
                new com.fptu.exe.skillswap.modules.admin.dto.response.AdminForumTopicResponse(
                        publicTopic.id(), publicTopic.code().name(), publicTopic.nameVi(), publicTopic.nameEn(), publicTopic.displayOrder()),
                publicPost.title(), publicPost.content(), publicPost.status(), publicPost.commentCount(), publicPost.reactionCount(),
                publicPost.reportCount(), publicPost.lastActivityAt(), publicPost.reactedByCurrentUser(), publicPost.myReactionType(),
                publicPost.createdAt(), publicPost.updatedAt(), publicPost.imageUrls());

        assertEquals(objectMapper.readTree(objectMapper.writeValueAsString(publicPost)),
                objectMapper.readTree(objectMapper.writeValueAsString(adminPost)));

        ForumCommentResponse publicComment = ForumCommentResponse.builder()
                .commentId(commentId).postId(postId).authorUserId(UUID.randomUUID()).authorFullName("User")
                .authorAvatarUrl("avatar").authorRole("MENTEE").content("Comment").status("VISIBLE")
                .reportCount(1).reactionCount(2).reactedByCurrentUser(false).replyToCommentId(null)
                .replyToUserId(null).replyToUserName(null).createdAt(LocalDateTime.of(2026, 9, 4, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 9, 4, 10, 0)).imageUrls(List.of())
                .build();
        AdminForumCommentResponse adminComment = new AdminForumCommentResponse(
                publicComment.commentId(), publicComment.postId(), publicComment.authorUserId(), publicComment.authorFullName(),
                publicComment.authorAvatarUrl(), publicComment.authorRole(), publicComment.content(), publicComment.status(),
                publicComment.reportCount(), publicComment.reactionCount(), publicComment.reactedByCurrentUser(),
                publicComment.replyToCommentId(), publicComment.replyToUserId(), publicComment.replyToUserName(),
                publicComment.createdAt(), publicComment.updatedAt(), publicComment.imageUrls());

        assertEquals(objectMapper.readTree(objectMapper.writeValueAsString(publicComment)),
                objectMapper.readTree(objectMapper.writeValueAsString(adminComment)));
    }

    @Test
    void adminReportAndInternalBoundariesKeepExistingJsonNames() throws Exception {
        ForumReportResponse publicReport = new ForumReportResponse(
                UUID.randomUUID(), "POST", UUID.randomUUID(), "PUBLISHED", "Title", "Preview",
                UUID.randomUUID(), "Author", UUID.randomUUID(), "Reporter", "SPAM", "Description",
                "OPEN", null, null, null, LocalDateTime.of(2026, 9, 4, 10, 0));
        AdminForumReportResponse adminReport = new AdminForumReportResponse(
                publicReport.reportId(), publicReport.targetType(), publicReport.targetId(), publicReport.targetStatus(),
                publicReport.targetTitle(), publicReport.targetContentPreview(), publicReport.targetAuthorUserId(),
                publicReport.targetAuthorFullName(), publicReport.reporterUserId(), publicReport.reporterFullName(),
                publicReport.reasonType(), publicReport.description(), publicReport.status(), publicReport.reviewedByUserId(),
                publicReport.reviewNote(), publicReport.resolvedAt(), publicReport.createdAt());
        assertEquals(objectMapper.readTree(objectMapper.writeValueAsString(publicReport)),
                objectMapper.readTree(objectMapper.writeValueAsString(adminReport)));

        PaymentCheckoutResponse payment = PaymentCheckoutResponse.builder()
                .paymentOrderId(UUID.randomUUID()).orderCode("1001").bookingId(UUID.randomUUID()).attemptNo(1)
                .status(PaymentOrderStatus.PENDING).providerOrderCode("provider-1001").providerPaymentLinkId("link-1001")
                .providerStatus("PENDING").checkoutUrl("https://pay.example/1001").paymentLink("https://pay.example/1001")
                .expiresAt(OffsetDateTime.parse("2026-09-04T11:00:00+07:00")).retryable(true).build();
        JsonNode paymentJson = objectMapper.readTree(objectMapper.writeValueAsString(payment));
        JsonNode internalPaymentJson = objectMapper.readTree(objectMapper.writeValueAsString(
                InternalPaymentWebhookResponse.from(payment)));
        assertEquals(paymentJson, internalPaymentJson);

        PresignedUploadResponse storage = PresignedUploadResponse.builder()
                .uploadUrl("https://upload.example").publicUrl(null).objectKey("internal/key").uploadMetadata(null).build();
        JsonNode storageJson = objectMapper.readTree(objectMapper.writeValueAsString(storage));
        JsonNode internalStorageJson = objectMapper.readTree(objectMapper.writeValueAsString(
                new InternalStorageUploadResponse("https://upload.example", null, "internal/key", null)));
        assertEquals(storageJson, internalStorageJson);
    }
}
