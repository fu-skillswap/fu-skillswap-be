package com.fptu.exe.skillswap.modules.conversation.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.conversation.dto.request.SendMessageRequest;
import com.fptu.exe.skillswap.modules.conversation.dto.response.ConversationResponse;
import com.fptu.exe.skillswap.modules.conversation.dto.response.MessageResponse;
import com.fptu.exe.skillswap.modules.conversation.repository.MessageRepository;
import com.fptu.exe.skillswap.modules.conversation.service.ConversationService;
import com.fptu.exe.skillswap.modules.conversation.service.ConversationSafetyService;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import com.fptu.exe.skillswap.shared.ratelimit.InMemoryRateLimitService;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/me/conversations")
@RequiredArgsConstructor
@Tag(name = "Conversation", description = "Nhóm API direct conversation gắn với booking effective. FE chỉ đọc/sync conversation do backend tự tạo.")
@SecurityRequirement(name = "bearerAuth")
public class ChatController {

    private final ConversationService conversationService;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final InMemoryRateLimitService rateLimitService;
    private final ConversationSafetyService conversationSafetyService;

    @GetMapping
    @Operation(
            summary = "Lấy danh sách conversation của tôi",
        description = "Trả về danh sách direct conversation của user hiện tại. Conversation được tạo tự động khi booking trở thành effective; FE dùng inbox thay vì tạo conversation thủ công."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Conversations loaded successfully",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "ConversationCursorPage",
                                    value = """
                                            {
                                              "timestamp": "2026-07-08 16:20:00",
                                              "status": 200,
                                              "code": "SUCCESS_0200",
                                              "message": "Thành công",
                                              "data": {
                                                "items": [
                                                  {
                                                    "id": "019f5234-aaaa-bbbb-cccc-1234567890ab",
                                                    "type": "DIRECT",
                                                    "status": "ACTIVE",
                                                    "otherUserId": "019f6234-aaaa-bbbb-cccc-1234567890ab",
                                                    "otherUserName": "Nguyen Van B",
                                                    "otherUserAvatarUrl": "https://cdn.skillswap.asia/avatar/b.jpg",
                                                    "lastMessageContent": "Anh da cap nhat meeting link.",
                                                    "lastMessageAt": "2026-07-08T15:55:00",
                                                    "createdAt": "2026-07-08T10:00:00",
                                                    "unreadCount": 2
                                                  }
                                                ],
                                                "nextCursor": "djEuQmFzZTY0VXJsSWYuLi5PcGFxdWVDdXJzb3I",
                                                "prevCursor": null,
                                                "hasNext": true,
                                                "hasPrev": false,
                                                "limit": 20
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "User is not authenticated")
    })
    public ApiResponse<CursorPageResponse<ConversationResponse>> getMyConversations(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(
                    description = "Opaque cursor string. Frontend không được cố gắng decode hay tự tạo chuỗi này; chỉ được lấy từ nextCursor của response trước đó để truyền lên.",
                    example = "djEuQmFzZTY0VXJsSWYuLi5PcGFxdWVDdXJzb3I"
            )
            @RequestParam(required = false) String cursor,
            @Parameter(description = "Số lượng item mong muốn cho một lần lấy dữ liệu. Mặc định 20, tối đa 50.", example = "20")
            @RequestParam(defaultValue = "20") Integer limit) {
        return ApiResponse.success(conversationService.getMyConversations(userPrincipal.getId(), cursor, limit));
    }

    @GetMapping("/{conversationId}/messages")
    @Operation(
            summary = "Lấy danh sách tin nhắn của conversation",
            description = "Trả về initial newest-first page hoặc sequence window. `beforeSequence` lấy cũ hơn; `afterSequence` dùng để repair reconnect. FE render oldest-to-newest sau khi sort nghiêm ngặt theo sequence."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Messages loaded successfully",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "MessageCursorPage",
                                    value = """
                                            {
                                              "timestamp": "2026-07-08 16:20:00",
                                              "status": 200,
                                              "code": "SUCCESS_0200",
                                              "message": "Thành công",
                                              "data": [
                                                  {
                                                    "id": "019f7234-aaaa-bbbb-cccc-1234567890ab",
                                                    "sequence": 128,
                                                    "conversationId": "019f5234-aaaa-bbbb-cccc-1234567890ab",
                                                    "senderId": "019f6234-aaaa-bbbb-cccc-1234567890ab",
                                                    "senderName": "Nguyen Van B",
                                                    "messageType": "TEXT",
                                                    "content": "Anh da cap nhat meeting link.",
                                                    "state": "ACTIVE",
                                                    "version": 0,
                                                    "createdAt": "2026-07-08T15:55:00",
                                                    "isMine": false
                                                  }
                                                ]
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "User is not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Current user is not a participant of the conversation"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Conversation not found")
    })
    public ApiResponse<java.util.List<MessageResponse>> getMessages(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID conversationId,
            @RequestParam(required = false) Long beforeSequence,
            @RequestParam(required = false) Long afterSequence,
            @Parameter(description = "Số lượng tin nhắn mong muốn cho một lần lấy dữ liệu. Mặc định 30, tối đa 50.", example = "30")
            @RequestParam(defaultValue = "30") Integer limit) {
        return ApiResponse.success(conversationService.getMessagesBySequence(conversationId, userPrincipal.getId(), beforeSequence, afterSequence, limit));
    }

    @PostMapping("/{conversationId}/messages")
    @Operation(
            summary = "Gửi tin nhắn trong conversation",
        description = "Gửi một tin nhắn text vào conversation hiện có mà user hiện tại đang tham gia. FE chỉ dùng API này sau khi booking effective đã tạo conversation."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Message sent successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "User is not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Current user is not a participant of the conversation"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Conversation not found")
    })
    public ApiResponse<MessageResponse> sendMessage(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest request) {
        rateLimitService.check(com.fptu.exe.skillswap.shared.ratelimit.RateLimitScope.BUSINESS,
                "chat:send:" + userPrincipal.getId(),
                30,
                java.time.Duration.ofMinutes(1),
                "Bạn đang gửi tin nhắn quá nhanh, vui lòng chậm lại một chút"
        );
        MessageResponse response = conversationService.sendMessage(conversationId, userPrincipal.getId(), request, messageRepository, userRepository);
        return ApiResponse.created(response);
    }

    @DeleteMapping("/{conversationId}/messages/{messageId}")
    @Operation(summary = "Delete my chat message", description = "Creates a tombstone and immediately revokes its attachment access; retention holds still control physical deletion.")
    public ApiResponse<MessageResponse> deleteMessage(@AuthenticationPrincipal UserPrincipal userPrincipal, @PathVariable UUID conversationId, @PathVariable UUID messageId, @Valid @RequestBody com.fptu.exe.skillswap.modules.conversation.dto.request.DeleteMessageRequest request) {
        return ApiResponse.success(conversationService.deleteMessage(conversationId, messageId, userPrincipal.getId(), request));
    }

    @PostMapping("/{conversationId}/attachment-upload-intents")
    @Operation(summary = "Create chat attachment upload intent", description = "Creates a private, short-lived upload intent. Attach the returned intent ID when sending a message; the client never provides an object key.")
    public ApiResponse<com.fptu.exe.skillswap.modules.conversation.dto.response.ChatAttachmentUploadIntentResponse> createAttachmentUploadIntent(@AuthenticationPrincipal UserPrincipal userPrincipal, @PathVariable UUID conversationId, @Valid @RequestBody com.fptu.exe.skillswap.modules.conversation.dto.request.ChatAttachmentUploadIntentRequest request) {
        return ApiResponse.created(conversationService.createAttachmentUploadIntent(conversationId, userPrincipal.getId(), request));
    }

    @GetMapping("/{conversationId}")
    @Operation(
            summary = "Lấy chi tiết conversation theo ID",
            description = "Trả về thông tin chi tiết (metadata) của một cuộc hội thoại mà user hiện tại đang tham gia."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Conversation detail loaded successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "User is not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Current user is not a participant of the conversation"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Conversation not found")
    })
    public ApiResponse<ConversationResponse> getConversationDetail(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID conversationId) {
        
        ConversationResponse response = conversationService.getConversationDetail(conversationId, userPrincipal.getId());
        return ApiResponse.success(response);
    }

    @GetMapping("/unread-count")
    @Operation(
            summary = "Lấy tổng số tin nhắn chưa đọc của tôi",
            description = "Trả về tổng số tin nhắn chưa đọc trên toàn bộ các active conversations của user hiện tại."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Unread count loaded successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "User is not authenticated")
    })
    public ApiResponse<java.util.Map<String, Long>> getTotalUnreadCount(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        long count = conversationService.getTotalUnreadCount(userPrincipal.getId());
        return ApiResponse.success(java.util.Map.of("totalUnreadCount", count));
    }

    @PatchMapping("/{conversationId}/read")
    @Operation(
            summary = "Đánh dấu cuộc hội thoại đã đọc",
            description = "Chỉ advance `lastReadSequence` của caller. Response tra ve canonical read cursor cua hai participant va unread count."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Conversation marked as read successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "User is not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Current user is not a participant of the conversation")
    })
    public ApiResponse<com.fptu.exe.skillswap.modules.conversation.dto.response.ConversationReadResponse> markConversationAsRead(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID conversationId,
            @Valid @RequestBody com.fptu.exe.skillswap.modules.conversation.dto.request.ConversationReadRequest request) {
        
        return ApiResponse.success(conversationService.markConversationAsRead(conversationId, userPrincipal.getId(), request.lastReadSequence()));
    }

    @PostMapping("/{conversationId}/block")
    @Operation(summary = "Chặn participant trong cuộc hội thoại", description = "Giữ lịch sử để đọc nhưng khóa gửi tin nhắn, upload và cấp URL tải file mới cho cả hai phía. Không thay đổi booking hoặc thanh toán.")
    public ApiResponse<com.fptu.exe.skillswap.modules.conversation.dto.response.ConversationBlockResponse> blockConversation(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID conversationId) {
        return ApiResponse.success(conversationSafetyService.block(conversationId, userPrincipal.getId()));
    }

    @DeleteMapping("/{conversationId}/block")
    @Operation(summary = "Bỏ chặn participant trong cuộc hội thoại", description = "Chỉ gỡ block do chính user hiện tại tạo. Booking-derived access sẽ được tính lại ngay.")
    public ApiResponse<com.fptu.exe.skillswap.modules.conversation.dto.response.ConversationBlockResponse> unblockConversation(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID conversationId) {
        return ApiResponse.success(conversationSafetyService.unblock(conversationId, userPrincipal.getId()));
    }

    @PostMapping("/{conversationId}/reports")
    @Operation(summary = "Report cuộc hội thoại", description = "Tạo một report moderation cho participant còn lại. Report không tự khóa booking hoặc tài khoản.")
    public ApiResponse<com.fptu.exe.skillswap.modules.conversation.dto.response.ChatReportResponse> reportConversation(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID conversationId,
            @Valid @RequestBody com.fptu.exe.skillswap.modules.conversation.dto.request.ChatReportCreateRequest request) {
        return ApiResponse.created(conversationSafetyService.createReport(conversationId, userPrincipal.getId(), request));
    }
}
