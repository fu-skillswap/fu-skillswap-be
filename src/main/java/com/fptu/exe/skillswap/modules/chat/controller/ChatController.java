package com.fptu.exe.skillswap.modules.chat.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.chat.dto.request.SendMessageRequest;
import com.fptu.exe.skillswap.modules.chat.dto.response.ConversationResponse;
import com.fptu.exe.skillswap.modules.chat.dto.response.MessageResponse;
import com.fptu.exe.skillswap.modules.chat.service.ConversationService;
import com.fptu.exe.skillswap.modules.chat.service.ConversationSafetyService;
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
    private final InMemoryRateLimitService rateLimitService;
    private final ConversationSafetyService conversationSafetyService;

    @GetMapping
    @Operation(
            summary = "Lấy danh sách conversation của tôi",
        description = "Trả về inbox của user hiện tại. Conversation có thể gắn với booking đã effective, course chat trực tiếp hoặc context course cũ; FE chỉ đọc conversation do backend cấp, không tự tạo conversation."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Lấy danh sách conversation thành công",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
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
                                    ),
                                    @ExampleObject(
                                            name = "CourseDirectConversation",
                                            value = """
                                                    {
                                                      "timestamp": "2026-09-04T03:20:00Z",
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
                                                            "lastMessageContent": "Em có thể hỏi anh trong khung chat khóa học này.",
                                                            "lastMessageAt": "2026-09-04T03:15:00Z",
                                                            "createdAt": "2026-09-01T08:00:00Z",
                                                            "unreadCount": 0,
                                                            "contextType": "COURSE_DIRECT",
                                                            "courseId": "019f4234-aaaa-bbbb-cccc-1234567890ab",
                                                            "courseTitle": "Spring Boot cho người mới"
                                                          }
                                                        ],
                                                        "nextCursor": null,
                                                        "prevCursor": null,
                                                        "hasNext": false,
                                                        "hasPrev": false,
                                                        "limit": 20
                                                      }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "LegacyCourseConversation",
                                            value = """
                                                    {
                                                      "timestamp": "2026-09-04T03:20:00Z",
                                                      "status": 200,
                                                      "code": "SUCCESS_0200",
                                                      "message": "Thành công",
                                                      "data": {
                                                        "items": [
                                                          {
                                                            "id": "019f7234-aaaa-bbbb-cccc-1234567890ab",
                                                            "type": "GROUP",
                                                            "status": "ACTIVE",
                                                            "lastMessageContent": "Chào mừng bạn vào nhóm khóa học.",
                                                            "lastMessageAt": "2026-09-03T10:00:00Z",
                                                            "createdAt": "2026-08-20T08:00:00Z",
                                                            "unreadCount": 1,
                                                            "contextType": "COURSE_GROUP",
                                                            "courseId": "019f4234-aaaa-bbbb-cccc-1234567890ab",
                                                            "courseTitle": "Spring Boot cho người mới",
                                                            "participantCount": 12
                                                          }
                                                        ],
                                                        "nextCursor": null,
                                                        "prevCursor": null,
                                                        "hasNext": false,
                                                        "hasPrev": false,
                                                        "limit": 20
                                                      }
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc access token không hợp lệ")
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
            description = "Trả về trang tin nhắn gần nhất hoặc một sequence window. `beforeSequence` lấy tin cũ hơn; `afterSequence` dùng để repair khi reconnect. FE render từ cũ đến mới sau khi sort theo sequence; không có tin nhắn là response 200 với mảng rỗng."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Lấy danh sách tin nhắn thành công",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
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
                                    ),
                                    @ExampleObject(
                                            name = "EmptyMessageHistory",
                                            value = """
                                                    {
                                                      "timestamp": "2026-09-04T03:20:00Z",
                                                      "status": 200,
                                                      "code": "SUCCESS_0200",
                                                      "message": "Thành công",
                                                      "data": []
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc access token không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "User hiện tại không phải participant hoặc conversation đang READ_ONLY"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy conversation"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Khoảng sequence không hợp lệ; tải lại message history và dùng sequence mới")
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
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Gửi tin nhắn thành công. FE lưu message id và sequence từ response; khi realtime gửi lại cùng message, deduplicate theo messageId.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "TextMessageCreated",
                                    value = """
                                            {
                                              "timestamp": "2026-09-04T03:21:00Z",
                                              "status": 201,
                                              "code": "CREATED_0201",
                                              "message": "Tạo mới thành công",
                                              "data": {
                                                "id": "019f8234-aaaa-bbbb-cccc-1234567890ab",
                                                "sequence": 129,
                                                "conversationId": "019f5234-aaaa-bbbb-cccc-1234567890ab",
                                                "senderId": "019f6234-aaaa-bbbb-cccc-1234567890ab",
                                                "senderName": "Nguyen Van A",
                                                "messageType": "TEXT",
                                                "content": "Chào anh, em đã xem meeting link.",
                                                "state": "ACTIVE",
                                                "version": 0,
                                                "editedAt": null,
                                                "deletedAt": null,
                                                "isReadByOther": false,
                                                "attachments": [],
                                                "createdAt": "2026-09-04T03:21:00Z",
                                                "isMine": true
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc access token không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Người dùng hiện tại không tham gia conversation"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy conversation"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Conflict: clientMessageId đã được dùng cho nội dung khác hoặc trạng thái conversation không cho phép gửi lại.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            name = "ClientMessageConflict",
                            value = """
                                    {
                                      "timestamp": "2026-09-04T03:21:02Z",
                                      "status": 409,
                                      "code": "CHAT_4101",
                                      "message": "Client message ID đã được dùng cho nội dung khác"
                                    }
                                    """
                    ))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Validation: clientMessageId bị thiếu, nội dung vượt quá 2000 ký tự hoặc attachment intent không hợp lệ.",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            name = "InvalidMessage",
                            value = """
                                    {
                                      "timestamp": "2026-09-04T03:21:02Z",
                                      "status": 400,
                                      "code": "VAL_3001",
                                      "message": "Nội dung tin nhắn không hợp lệ"
                                    }
                                    """
                    ))
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "clientMessageId là UUID do FE tạo và giữ ổn định trong một lần gửi; nếu request timeout, retry cùng body để tránh tạo tin nhắn trùng. Người gửi lấy từ JWT, FE không gửi senderId.",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(
                            name = "SendTextMessage",
                            value = """
                                    {
                                      "clientMessageId": "019f7234-aaaa-bbbb-cccc-1234567890ab",
                                      "content": "Chào anh, em đã xem meeting link.",
                                      "replyToMessageId": null,
                                      "attachmentIntentIds": []
                                    }
                                    """
                    )
            )
    )
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
        MessageResponse response = conversationService.sendMessage(conversationId, userPrincipal.getId(), request);
        return ApiResponse.created(response);
    }

    @DeleteMapping("/{conversationId}/messages/{messageId}")
    @Operation(summary = "Xóa tin nhắn của tôi", description = "Ẩn tin nhắn khỏi conversation và thu hồi quyền tải file đính kèm. Việc xóa dữ liệu vật lý thực tế tuân theo chính sách lưu trữ của hệ thống.")
    public ApiResponse<MessageResponse> deleteMessage(@AuthenticationPrincipal UserPrincipal userPrincipal, @PathVariable UUID conversationId, @PathVariable UUID messageId, @Valid @RequestBody com.fptu.exe.skillswap.modules.chat.dto.request.DeleteMessageRequest request) {
        return ApiResponse.success(conversationService.deleteMessage(conversationId, messageId, userPrincipal.getId(), request));
    }

    @PostMapping("/{conversationId}/attachment-upload-intents")
    @Operation(summary = "Tạo upload intent cho file chat", description = "Tạo quyền upload private có thời hạn ngắn. FE gửi intent ID nhận được khi tạo tin nhắn; không tự gửi object key.")
    public ApiResponse<com.fptu.exe.skillswap.modules.chat.dto.response.ChatAttachmentUploadIntentResponse> createAttachmentUploadIntent(@AuthenticationPrincipal UserPrincipal userPrincipal, @PathVariable UUID conversationId, @Valid @RequestBody com.fptu.exe.skillswap.modules.chat.dto.request.ChatAttachmentUploadIntentRequest request) {
        return ApiResponse.created(conversationService.createAttachmentUploadIntent(conversationId, userPrincipal.getId(), request));
    }

    @GetMapping("/{conversationId}")
    @Operation(
            summary = "Lấy chi tiết conversation theo ID",
            description = "Trả về thông tin chi tiết (metadata) của một cuộc hội thoại mà user hiện tại đang tham gia."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy chi tiết conversation thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc access token không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Người dùng hiện tại không tham gia conversation"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy conversation")
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
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy tổng số tin chưa đọc thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc access token không hợp lệ")
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
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đánh dấu conversation đã đọc thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc access token không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Người dùng hiện tại không tham gia conversation")
    })
    public ApiResponse<com.fptu.exe.skillswap.modules.chat.dto.response.ConversationReadResponse> markConversationAsRead(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID conversationId,
            @Valid @RequestBody com.fptu.exe.skillswap.modules.chat.dto.request.ConversationReadRequest request) {
        
        return ApiResponse.success(conversationService.markConversationAsRead(conversationId, userPrincipal.getId(), request.lastReadSequence()));
    }

    @PostMapping("/{conversationId}/block")
    @Operation(summary = "Chặn participant trong cuộc hội thoại", description = "Giữ lịch sử để đọc nhưng khóa gửi tin nhắn, upload và cấp URL tải file mới cho cả hai phía. Không thay đổi booking hoặc thanh toán.")
    public ApiResponse<com.fptu.exe.skillswap.modules.chat.dto.response.ConversationBlockResponse> blockConversation(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID conversationId) {
        return ApiResponse.success(conversationSafetyService.block(conversationId, userPrincipal.getId()));
    }

    @DeleteMapping("/{conversationId}/block")
    @Operation(summary = "Bỏ chặn participant trong cuộc hội thoại", description = "Chỉ gỡ block do chính user hiện tại tạo. Booking-derived access sẽ được tính lại ngay.")
    public ApiResponse<com.fptu.exe.skillswap.modules.chat.dto.response.ConversationBlockResponse> unblockConversation(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID conversationId) {
        return ApiResponse.success(conversationSafetyService.unblock(conversationId, userPrincipal.getId()));
    }

    @PostMapping("/{conversationId}/reports")
    @Operation(summary = "Report cuộc hội thoại", description = "Tạo một report moderation cho participant còn lại. Report không tự khóa booking hoặc tài khoản.")
    public ApiResponse<com.fptu.exe.skillswap.modules.chat.dto.response.ChatReportResponse> reportConversation(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID conversationId,
            @Valid @RequestBody com.fptu.exe.skillswap.modules.chat.dto.request.ChatReportCreateRequest request) {
        return ApiResponse.created(conversationSafetyService.createReport(conversationId, userPrincipal.getId(), request));
    }
}
