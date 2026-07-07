package com.fptu.exe.skillswap.modules.conversation.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.conversation.dto.request.SendMessageRequest;
import com.fptu.exe.skillswap.modules.conversation.dto.response.ConversationResponse;
import com.fptu.exe.skillswap.modules.conversation.dto.response.MessageResponse;
import com.fptu.exe.skillswap.modules.conversation.repository.MessageRepository;
import com.fptu.exe.skillswap.modules.conversation.service.ConversationService;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.ratelimit.InMemoryRateLimitService;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
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
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Conversations loaded successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "User is not authenticated")
    })
    public ApiResponse<PageResponse<ConversationResponse>> getMyConversations(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @ParameterObject @ModelAttribute BasePageRequest pageRequest) {
        
        Page<ConversationResponse> page = conversationService.getMyConversations(userPrincipal.getId(), pageRequest.getPageable());
        return ApiResponse.success(PageResponse.<ConversationResponse>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build());
    }

    @GetMapping("/{conversationId}/messages")
    @Operation(
            summary = "Lấy danh sách tin nhắn của conversation",
            description = "Trả về danh sách tin nhắn của một conversation khi user hiện tại là participant của conversation đó. FE dùng sau khi user mở một thread chat; thứ tự tin nhắn hiện tại là mới nhất trước."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Messages loaded successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "User is not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Current user is not a participant of the conversation"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Conversation not found")
    })
    public ApiResponse<PageResponse<MessageResponse>> getMessages(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID conversationId,
            @ParameterObject @ModelAttribute BasePageRequest pageRequest) {
        
        Page<MessageResponse> page = conversationService.getMessages(conversationId, userPrincipal.getId(), pageRequest.getPageable(), messageRepository);
        return ApiResponse.success(PageResponse.<MessageResponse>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build());
    }

    @PostMapping("/{conversationId}/messages")
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
