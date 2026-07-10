package com.fptu.exe.skillswap.modules.conversation.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.conversation.dto.request.SendMessageRequest;
import com.fptu.exe.skillswap.modules.conversation.dto.response.ConversationResponse;
import com.fptu.exe.skillswap.modules.conversation.dto.response.MessageResponse;
import com.fptu.exe.skillswap.modules.conversation.repository.MessageRepository;
import com.fptu.exe.skillswap.modules.conversation.service.ConversationService;
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
@Tag(name = "Conversation", description = "Nhóm API lấy danh sách conversation và gửi/đọc tin nhắn gắn với booking đã được accept. FE dùng sau khi hệ thống đã tự tạo conversation cho booking hợp lệ.")
@SecurityRequirement(name = "bearerAuth")
public class ChatController {

    private final ConversationService conversationService;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final InMemoryRateLimitService rateLimitService;

    @GetMapping
    @Operation(
            summary = "Lấy danh sách conversation của tôi",
            description = "Trả về danh sách conversation của user hiện tại. Trong flow hiện tại, conversation được tạo tự động sau khi booking được accept, nên FE phải dùng API này để dựng inbox thay vì cố tạo conversation thủ công."
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
                                                    "sourceType": "BOOKING",
                                                    "sourceId": "019f4234-aaaa-bbbb-cccc-1234567890ab",
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
            description = "Trả về danh sách tin nhắn của một conversation khi user hiện tại là participant của conversation đó. FE dùng sau khi user mở một thread chat; thứ tự tin nhắn hiện tại là mới nhất trước."
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
                                              "data": {
                                                "items": [
                                                  {
                                                    "id": "019f7234-aaaa-bbbb-cccc-1234567890ab",
                                                    "conversationId": "019f5234-aaaa-bbbb-cccc-1234567890ab",
                                                    "senderId": "019f6234-aaaa-bbbb-cccc-1234567890ab",
                                                    "senderName": "Nguyen Van B",
                                                    "messageType": "TEXT",
                                                    "content": "Anh da cap nhat meeting link.",
                                                    "createdAt": "2026-07-08T15:55:00",
                                                    "isMine": false
                                                  }
                                                ],
                                                "nextCursor": "djEuQmFzZTY0VXJsSWYuLi5PcGFxdWVDdXJzb3I",
                                                "prevCursor": null,
                                                "hasNext": true,
                                                "hasPrev": false,
                                                "limit": 30
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "User is not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Current user is not a participant of the conversation"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Conversation not found")
    })
    public ApiResponse<CursorPageResponse<MessageResponse>> getMessages(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID conversationId,
            @Parameter(
                    description = "Opaque cursor string. Frontend không được cố gắng decode hay tự tạo chuỗi này; chỉ được lấy từ nextCursor của response trước đó để truyền lên.",
                    example = "djEuQmFzZTY0VXJsSWYuLi5PcGFxdWVDdXJzb3I"
            )
            @RequestParam(required = false) String cursor,
            @Parameter(description = "Số lượng tin nhắn mong muốn cho một lần lấy dữ liệu. Mặc định 30, tối đa 50.", example = "30")
            @RequestParam(defaultValue = "30") Integer limit) {
        return ApiResponse.success(conversationService.getMessages(conversationId, userPrincipal.getId(), cursor, limit));
    }

    @PostMapping("/{conversationId}/messages")
    @com.fptu.exe.skillswap.shared.idempotency.Idempotent
    @Operation(
            summary = "Gửi tin nhắn trong conversation",
            description = "Gửi một tin nhắn text vào conversation hiện có mà user hiện tại đang tham gia. FE chỉ dùng API này sau khi booking liên quan đã tạo conversation thông qua flow accept booking."
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
        rateLimitService.check(
                "chat:send:" + userPrincipal.getId(),
                30,
                java.time.Duration.ofMinutes(1),
                "Bạn đang gửi tin nhắn quá nhanh, vui lòng chậm lại một chút"
        );
        MessageResponse response = conversationService.sendMessage(conversationId, userPrincipal.getId(), request, messageRepository, userRepository);
        return ApiResponse.created(response);
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
            description = "Cập nhật thời điểm đọc tin nhắn cuối cùng của user hiện tại trong cuộc hội thoại thành thời gian hiện tại."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Conversation marked as read successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "User is not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Current user is not a participant of the conversation")
    })
    public ApiResponse<String> markConversationAsRead(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID conversationId) {
        
        conversationService.markConversationAsRead(conversationId, userPrincipal.getId());
        return ApiResponse.success("Đánh dấu đã đọc thành công");
    }
}
