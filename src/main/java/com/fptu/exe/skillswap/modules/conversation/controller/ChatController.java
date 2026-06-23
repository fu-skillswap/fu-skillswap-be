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
@Tag(name = "Chat & Conversation", description = "API quản lý hộp thư và tin nhắn của user")
public class ChatController {

    private final ConversationService conversationService;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "Lấy danh sách các cuộc hội thoại của tôi")
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
    @Operation(summary = "Lấy danh sách tin nhắn trong một cuộc hội thoại")
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
    @Operation(summary = "Gửi tin nhắn")
    public ApiResponse<MessageResponse> sendMessage(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest request) {
        
        MessageResponse response = conversationService.sendMessage(conversationId, userPrincipal.getId(), request, messageRepository, userRepository);
        return ApiResponse.created(response);
    }
}
