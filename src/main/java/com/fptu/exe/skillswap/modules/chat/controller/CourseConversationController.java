package com.fptu.exe.skillswap.modules.chat.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.chat.dto.response.CourseConversationResponse;
import com.fptu.exe.skillswap.modules.chat.service.CourseConversationService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/courses/{courseId}/chat")
@RequiredArgsConstructor
@Tag(name = "Course chat", description = "Mở cuộc trò chuyện trong phạm vi khóa học với mentor của khóa học hiện tại.")
@SecurityRequirement(name = "bearerAuth")
public class CourseConversationController {

    private final CourseConversationService courseConversationService;

    @PostMapping
    @Operation(summary = "Mở hoặc tạo cuộc trò chuyện của khóa học", description = "User đã đăng nhập gọi khi mở khu vực chat của khóa học. Backend chỉ trả conversation hiện có hoặc tạo mới cho người học có enrollment ACTIVE, khi mentor của khóa học vẫn active và đủ điều kiện chat. FE không gửi menteeId/mentorId và không tự tạo conversation.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Trả conversation hiện có hoặc conversation mới; FE dùng conversationId để gọi API messages và subscribe realtime.", content = @Content(examples = @ExampleObject(
                    name = "CourseDirectChat",
                    value = """
                            {
                              "status": 200,
                              "code": "SUCCESS_0200",
                              "message": "Thành công",
                              "data": {
                                "conversationId": "019f5234-aaaa-bbbb-cccc-1234567890ab",
                                "courseId": "019f1234-aaaa-bbbb-cccc-1234567890ab",
                                "courseTitle": "Spring Boot cho người mới",
                                "contextType": "COURSE_DIRECT",
                                "mentorUserId": "019f6234-aaaa-bbbb-cccc-1234567890ab",
                                "mentorName": "Nguyen Van B",
                                "mentorAvatarUrl": "https://cdn.example/avatar.jpg",
                                "conversation": {
                                  "id": "019f5234-aaaa-bbbb-cccc-1234567890ab",
                                  "type": "DIRECT",
                                  "status": "ACTIVE",
                                  "messagingAccess": "OPEN",
                                  "canSendMessages": true,
                                  "canUploadAttachments": true,
                                  "canDownloadAttachments": true,
                                  "unreadCount": 0
                                }
                              }
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc access token không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "User chưa có enrollment ACTIVE, tài khoản không hoạt động hoặc mentor hiện không đủ điều kiện chat"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy khóa học"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Xung đột khi hai request đồng thời cùng tạo course conversation; FE có thể tải lại conversation sau đó")
    })
    public ApiResponse<CourseConversationResponse> openChat(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId) {
        return ApiResponse.success(courseConversationService.getOrCreate(courseId, principal.getId()));
    }
}
